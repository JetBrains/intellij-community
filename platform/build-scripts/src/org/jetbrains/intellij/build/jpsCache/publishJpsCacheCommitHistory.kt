// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.jpsCache

import com.intellij.openapi.util.Ref
import io.netty.handler.codec.http.HttpHeaderNames
import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http2.Http2Headers
import io.netty.util.AsciiString
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.UnexpectedHttpStatus
import org.jetbrains.intellij.build.http2Client.checkMirrorAndConnect
import org.jetbrains.intellij.build.http2Client.getJsonOrDefaultIfNotFound
import org.jetbrains.intellij.build.http2Client.getString
import org.jetbrains.intellij.build.http2Client.upload
import org.jetbrains.intellij.build.http2Client.withHttp2ClientConnectionFactory
import org.jetbrains.intellij.build.impl.compilation.checkExists
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

/**
 * Publish already uploaded JPS Cache.
 */
suspend fun publishUploadedJpsCacheCache(overrideCommits: Set<String>? = null) {
  updateJpsCacheCommitHistory(
    overrideCommits = overrideCommits,
    remoteGitUrl = jpsCacheRemoteGitUrl,
    commitHash = jpsCacheCommit,
    uploadUrl = jpsCacheUploadUrl,
    authHeader = jpsCacheAuthHeader,
    s3Dir = jpsCacheS3Dir,
  )
}

/**
 * Upload and publish a file with commits history
 */
internal suspend fun updateJpsCacheCommitHistory(
  overrideCommits: Set<String>?,
  remoteGitUrl: String,
  commitHash: String,
  uploadUrl: URI,
  authHeader: CharSequence?,
  s3Dir: Path?,
) {
  val overrideRemoteHistory = overrideCommits != null
  val commits = overrideCommits ?: setOf(commitHash)
  spanBuilder("update JPS Cache commit history")
    .setAttribute("url", uploadUrl.toString())
    .setAttribute("overrideRemoteHistory", overrideRemoteHistory)
    .setAttribute("s3Dir", s3Dir?.toString() ?: "")
    .setAttribute(AttributeKey.stringArrayKey("commits"), java.util.List.copyOf(commits))
    .use {
      val commitHistory = CommitHistory(java.util.Map.of(remoteGitUrl, commits))
      withHttp2ClientConnectionFactory(trustAll = uploadUrl.host == "127.0.0.1") { client ->
        checkMirrorAndConnect(initialServerUri = uploadUrl, authHeader = authHeader, client = client) { connection, urlPathPrefix ->
          val uploaded = checkThatJpsCacheWasUploaded(
            commitHistory = commitHistory,
            remoteGitUrl = remoteGitUrl,
            urlPathPrefix = urlPathPrefix,
            connection = connection,
            overrideRemoteHistory = overrideRemoteHistory,
          )
          if (!uploaded) {
            return@checkMirrorAndConnect
          }

          val (newHistory: CommitHistory, serializedNewHistory) = withCheckingRemoteModifications(
            overrideRemoteHistory, commitHistory, connection,
            urlPathPrefix
          ) { content, headers ->
            client.connect(address = uploadUrl, authHeader = authHeader).use { connectionForPut ->
              val path = "${uploadUrl.path}/$COMMIT_HISTORY_JSON_FILE"
              connectionForPut.upload(path, content, headers)
            }
          }
          if (s3Dir != null) {
            val commitHistoryFile = s3Dir.resolve(COMMIT_HISTORY_JSON_FILE)
            Files.createDirectories(commitHistoryFile.parent)
            Files.writeString(commitHistoryFile, serializedNewHistory)
            Span.current().addEvent(
              "write commit history",
              Attributes.of(
                AttributeKey.stringKey("data"), serializedNewHistory,
                AttributeKey.stringKey("file"), commitHistoryFile.toString()
              ),
            )
          }

          verify(newHistory = newHistory, remoteGitUrl = remoteGitUrl, connectionForGet = connection, urlPathPrefixForGet = urlPathPrefix)
        }
      }
    }
}

private suspend fun withCheckingRemoteModifications(
  overrideRemoteHistory: Boolean,
  commitHistory: CommitHistory,
  connection: Http2ClientConnection,
  urlPathPrefix: String,
  doUpload: suspend (String, List<AsciiString>) -> Unit,
): Pair<CommitHistory, String> {
  if (overrideRemoteHistory) {
    val serializedNewHistory = commitHistory.toJson()
    doUpload(serializedNewHistory, emptyList())
    return Pair(commitHistory, serializedNewHistory)
  }
  var attempts = 0
  while (true) {
    val responseHeaders = Ref<Http2Headers>()
    val content = connection.getString("$urlPathPrefix/$COMMIT_HISTORY_JSON_FILE", responseHeaders)
    val remoteCommitHistory = CommitHistory(content)

    val newHistory = commitHistory + remoteCommitHistory
    val serializedNewHistory = newHistory.toJson()

    val uploadHeaders = arrayListOf(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
    responseHeaders.get().get(HttpHeaderNames.ETAG)?.let { etag ->
      uploadHeaders.add(HttpHeaderNames.IF_MATCH)
      uploadHeaders.add(AsciiString.of(etag.removePrefix("W/"))) // when compressing response (gzip), nginx marks etag as weak
    }
    responseHeaders.get().get(HttpHeaderNames.LAST_MODIFIED)?.let { date ->
      uploadHeaders.add(HttpHeaderNames.IF_UNMODIFIED_SINCE)
      uploadHeaders.add(AsciiString.of(date))
    }
    try {
      doUpload(serializedNewHistory, uploadHeaders)
      return Pair(newHistory, serializedNewHistory)
    }
    catch (e: Throwable) {
      if (attempts > 10) throw e
      if (e is UnexpectedHttpStatus) {
        val code = e.status.code()
        if (code == 409 || code == 412) {
          Span.current().addEvent("got $code response, will try to upload commit history again")
          attempts++
          continue
        }
      }
      throw e
    }
  }
}

private suspend fun checkThatJpsCacheWasUploaded(
  commitHistory: CommitHistory,
  remoteGitUrl: String,
  urlPathPrefix: String,
  connection: Http2ClientConnection,
  overrideRemoteHistory: Boolean,
): Boolean {
  for (commitHashForRemote in commitHistory.commitsForRemote(remoteGitUrl)) {
    val cacheUrl = "$urlPathPrefix/caches/$commitHashForRemote.zip.zstd"
    val cacheUploaded = checkExists(connection, cacheUrl)
    val metadataUrlPath = "$urlPathPrefix/metadata/$commitHashForRemote"
    val metadataUploaded = checkExists(connection, metadataUrlPath)
    if (!cacheUploaded && !metadataUploaded) {
      val message = "Unable to publish $commitHashForRemote due to missing $cacheUrl and $metadataUrlPath. Probably caused by previous cleanup build."
      if (overrideRemoteHistory) {
        throw RuntimeException(message)
      }
      else {
        Span.current().addEvent(message)
      }
      return false
    }
    check(cacheUploaded == metadataUploaded) {
      "JPS Caches are uploaded: $cacheUploaded, metadata is uploaded: $metadataUploaded"
    }
  }
  return true
}

private suspend fun verify(
  newHistory: CommitHistory,
  remoteGitUrl: String,
  connectionForGet: Http2ClientConnection,
  urlPathPrefixForGet: String,
) {
  val expected = newHistory.commitsForRemote(remoteGitUrl).toSet()
  retryWithExponentialBackOff {
    val actual = getRemoteCommitHistory(connectionForGet, urlPathPrefixForGet).commitsForRemote(remoteGitUrl).toSet()
    val missing = expected - actual
    check(missing.none()) {
      "Missing: $missing"
    }
  }
}

private suspend fun getRemoteCommitHistory(connection: Http2ClientConnection, urlPathPrefix: String): CommitHistory {
  return CommitHistory(connection.getJsonOrDefaultIfNotFound(path = "$urlPathPrefix/$COMMIT_HISTORY_JSON_FILE", defaultIfNotFound = emptyMap()))
}