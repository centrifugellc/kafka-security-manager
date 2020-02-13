package com.github.simplesteph.ksm.source

import java.io.StringReader
import java.nio.charset.Charset
import java.util.Base64

import com.fasterxml.jackson.databind.ObjectMapper
import com.github.simplesteph.ksm.parser.AclParser
import com.typesafe.config.Config
import org.slf4j.LoggerFactory
import skinny.http.{HTTP, HTTPException, Request, Response}

import scala.util.Try

class GitLabSourceAcl extends SourceAcl {

  private val log = LoggerFactory.getLogger(classOf[GitLabSourceAcl])

  override val CONFIG_PREFIX: String = "gitlab"  
  final val REPOID_CONFIG = "repoid"
  final val FILEPATH_CONFIG = "filepath"
  final val BRANCH_CONFIG = "branch"
  final val HOSTNAME_CONFIG = "hostname"  
  final val ACCESSTOKEN_CONFIG = "accesstoken"

  var lastModified: Option[String] = None
  val objectMapper = new ObjectMapper()
  var repoid: String = _
  var filepath: String = _
  var branch: String = _
  var hostname: String = _
  var accessToken: String = _

  /**
    * internal config definition for the module
    */
  override def configure(config: Config): Unit = {    
    repoid = config.getString(REPOID_CONFIG)
    filepath = config.getString(FILEPATH_CONFIG)
    branch = config.getString(BRANCH_CONFIG)
    hostname = config.getString(HOSTNAME_CONFIG)
    accessToken = config.getString(ACCESSTOKEN_CONFIG)
  }

  override def refresh(aclParser: AclParser): Option[SourceAclResult] = {
    val url =
      s"https://$hostname/api/v4/projects/$repoid/repository/files/$filepath?ref=$branch"
    val request: Request = new Request(url)

    // auth header
    request.header("PRIVATE-TOKEN", s" $accessToken")

    val response: Response = HTTP.get(request)

    response.status match {
      case 200 =>
        val responseJSON = objectMapper.readTree(response.textBody)
        val commitId = responseJSON.get("commit_id").asText()
        if (lastModified == Some(commitId)) {
          log.info(s"No changes were detected in the ACL file ${filepath}. Skipping .... ")
          None
        } else {
          lastModified = Some(commitId)
          val b64encodedContent = responseJSON.get("content").asText()
          val data = new String(
            Base64.getDecoder.decode(
              b64encodedContent.replace("\n", "").replace("\r", "")),
            Charset.forName("UTF-8"))
          // use the CSV Parser
          Some(aclParser.aclsFromReader(new StringReader(data)))
        }        
      case 304 =>
        None
      case _ =>
        // we got an http error so we propagate it
        log.warn(response.asString)
        Some(
          SourceAclResult(
            Set(),
            List(Try(
              throw HTTPException(Some("Failure to fetch file"), response)))))
    }
  }

  /**
    * Close all the necessary underlying objects or connections belonging to this instance
    */
  override def close(): Unit = {
    // HTTP
  }
}
