// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(ExperimentalPathApi::class)
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.stream.JsonReader
import io.netty.handler.codec.http.HttpResponseStatus
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.getJsonOrDefaultIfNotFound
import org.jetbrains.intellij.build.http2Client.upload
import org.jetbrains.intellij.build.http2Client.withHttp2ClientConnectionFactory
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.getAllCompilationOutputs
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.moveFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.net.URI
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively
import kotlin.random.Random

private const val SOURCES_STATE_FILE_NAME = "target_sources_state.json"

internal suspend fun uploadJpsCache(
  forcedUpload: Boolean,
  commitHash: String,
  authHeader: CharSequence?,
  s3Dir: Path?,
  uploadUrl: URI,
  context: CompilationContext,
) {
  if (s3Dir != null) {
    s3Dir.deleteRecursively()
    Files.createDirectories(s3Dir)
  }

  val sourceStateFile = context.compilationData.dataStorageRoot.resolve(SOURCES_STATE_FILE_NAME)
  val sourceState = try {
    Files.newBufferedReader(sourceStateFile).use {
      BuildTargetSourcesState.readJson(JsonReader(it))
    }
  }
  catch (e: NoSuchFileException) {
    throw IllegalStateException("Compilation outputs doesn't contain source state file, please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag", e)
  }

  val start = System.nanoTime()
  val totalUploadedBytes = LongAdder()
  val uploadedOutputCount = LongAdder()
  val messages = context.messages
  withHttp2ClientConnectionFactory(trustAll = uploadUrl.host == "127.0.0.1") { client ->
    client.connect(host = uploadUrl.host, port = uploadUrl.port, authHeader = authHeader).use { connection ->
      val urlPathPrefix = uploadUrl.path
      withContext(Dispatchers.IO) {
        launch {
          spanBuilder("upload jps cache").use {
            uploadJpsCaches(urlPathPrefix = urlPathPrefix, connection = connection, s3Dir = s3Dir, forcedUpload = forcedUpload, commitHash = commitHash, context = context)
          }
        }

        spanBuilder("upload compilation outputs").use {
          val allCompilationOutputs = getAllCompilationOutputs(sourceState = sourceState, classOutDir = context.classesOutputDirectory)
          val tempDir = Files.createTempDirectory(context.paths.tempDir, "jps-cache-bytecode-")
          try {
            uploadCompilationOutputs(
              uploadedOutputCount = uploadedOutputCount,
              totalUploadedBytes = totalUploadedBytes,
              allCompilationOutputs = allCompilationOutputs,
              urlPathPrefix = urlPathPrefix,
              connection = connection,
              s3Dir = s3Dir,
              forcedUpload = forcedUpload,
              tempDir = tempDir,
            )
          }
          finally {
            tempDir.deleteRecursively()
          }
          messages.reportStatisticValue("Total outputs", allCompilationOutputs.size.toString())
        }
      }

      val metadataPath = "metadata/$commitHash"
      val metadataUrl = "$urlPathPrefix/$metadataPath"
      spanBuilder("upload metadata").setAttribute("path", metadataUrl).use {
        connection.upload(metadataUrl, sourceStateFile)
        if (s3Dir != null) {
          copyFile(file = sourceStateFile, target = s3Dir.resolve(metadataPath))
        }
      }
    }
  }

  messages.reportStatisticValue("jps-cache:upload:time", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toString())
  messages.reportStatisticValue("jps-cache:uploaded:count", (uploadedOutputCount.sum() + 1).toString())
  messages.reportStatisticValue("jps-cache:uploaded:bytes", totalUploadedBytes.sum().toString())

  messages.reportStatisticValue("Uploaded outputs", uploadedOutputCount.sum().toString())

  if (s3Dir != null) {
    uploadToS3(s3Dir)
  }
}

private suspend fun uploadToS3(s3Dir: Path) {
  spanBuilder("aws s3 sync").use {
    awsS3Cli("cp", "--no-progress", "--include", "*", "--recursive", s3Dir.toString(), "s3://intellij-jps-cache", returnStdOut = false)
  }
}

private suspend fun uploadJpsCaches(
  urlPathPrefix: String,
  connection: Http2ClientConnection,
  commitHash: String,
  forcedUpload: Boolean,
  s3Dir: Path?,
  context: CompilationContext,
) {
  val dataStorageRoot = context.compilationData.dataStorageRoot
  val zipFile = context.paths.tempDir.resolve("$commitHash-${java.lang.Long.toUnsignedString(Random.nextLong(), Character.MAX_RADIX)}.zip")
  try {
    val compressed by lazy {
      zipWithCompression(zipFile, mapOf(dataStorageRoot to ""))
      zipFile
    }

    val cachePath = "caches/$commitHash"
    val urlPath = "$urlPathPrefix/$cachePath"
    if (forcedUpload || !checkExists(connection, urlPath, logIfExists = true)) {
      connection.upload(path = urlPath, file = compressed)
    }

    if (s3Dir != null) {
      moveFile(compressed, s3Dir.resolve(cachePath))
    }
  }
  finally {
    Files.deleteIfExists(zipFile)
  }
}

private suspend fun uploadCompilationOutputs(
  uploadedOutputCount: LongAdder,
  allCompilationOutputs: List<CompilationOutput>,
  urlPathPrefix: String,
  connection: Http2ClientConnection,
  totalUploadedBytes: LongAdder,
  forcedUpload: Boolean,
  tempDir: Path,
  s3Dir: Path?,
) {
  allCompilationOutputs.forEachConcurrent(uploadParallelism) { compilationOutput ->
    val outDir = compilationOutput.path
    spanBuilder("upload output part")
      .setAttribute("part", compilationOutput.remotePath)
      .setAttribute("path", outDir.toString())
      .use { span ->
        if (Files.notExists(outDir)) {
          span.addEvent("doesn't exist, was a respective module removed?")
          return@use
        }

        val sourcePath = compilationOutput.remotePath
        val zipFile = tempDir.resolve("${sourcePath.replace('/', '_')}.zip")
        zipWithCompression(targetFile = zipFile, dirs = mapOf(outDir to ""), createFileParentDirs = false)

        val urlPath = "$urlPathPrefix/$sourcePath"
        if (forcedUpload || !checkExists(connection, urlPath)) {
          spanBuilder("upload").setAttribute("urlPath", urlPath).setAttribute("path", sourcePath).use {
            val result = connection.upload(path = urlPath, file = zipFile)
            totalUploadedBytes.add(result.uploadedSize)
          }
          uploadedOutputCount.increment()
        }

        if (s3Dir != null) {
          moveFile(zipFile, s3Dir.resolve(sourcePath))
        }
      }
  }
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
  context: CompilationContext,
) {
  val overrideRemoteHistory = overrideCommits != null
  val commitHistory = CommitsHistory(mapOf(remoteGitUrl to (overrideCommits ?: setOf(commitHash))))
  withHttp2ClientConnectionFactory(trustAll = uploadUrl.host == "127.0.0.1") { client ->
    val urlPathPrefix = uploadUrl.path
    client.connect(uploadUrl.host, uploadUrl.port, authHeader = authHeader).use { connection ->
      for (commitHashForRemote in commitHistory.commitsForRemote(remoteGitUrl)) {
        val cacheUploaded = checkExists(connection, "$urlPathPrefix/caches/$commitHashForRemote")
        val metadataUploaded = checkExists(connection, "$urlPathPrefix/metadata/$commitHashForRemote")
        if (!cacheUploaded && !metadataUploaded) {
          val msg = "Unable to publish $commitHashForRemote due to missing caches/$commitHashForRemote and metadata/$commitHashForRemote." +
                    " Probably caused by previous cleanup build."
          if (overrideRemoteHistory) {
            context.messages.error(msg)
          }
          else {
            context.messages.warning(msg)
          }
          return@use false
        }
        check(cacheUploaded == metadataUploaded) {
          "JPS Caches are uploaded: $cacheUploaded, metadata is uploaded: $metadataUploaded"
        }
      }

      val newHistory = if (overrideRemoteHistory) commitHistory else commitHistory + remoteCommitHistory(connection, urlPathPrefix)
      connection.upload(path = "$urlPathPrefix/${CommitsHistory.JSON_FILE}", file = writeCommitHistory(commitHistory = newHistory, context = context, s3Dir = s3Dir))
      val expected = newHistory.commitsForRemote(remoteGitUrl).toSet()
      val actual = remoteCommitHistory(connection, urlPathPrefix).commitsForRemote(remoteGitUrl).toSet()
      val missing = expected - actual
      val unexpected = actual - expected
      check(missing.none() && unexpected.none()) {
        """
          Missing: $missing
          Unexpected: $unexpected
        """.trimIndent()
      }
    }
  }
}

private suspend fun remoteCommitHistory(connection: Http2ClientConnection, urlPathPrefix: String): CommitsHistory {
  return CommitsHistory(connection.getJsonOrDefaultIfNotFound(path = "$urlPathPrefix/${CommitsHistory.JSON_FILE}", defaultIfNotFound = emptyMap()))
}

private fun writeCommitHistory(commitHistory: CommitsHistory, context: CompilationContext, s3Dir: Path?): Path {
  val commitHistoryFile = (s3Dir ?: context.paths.tempDir).resolve(CommitsHistory.JSON_FILE)
  Files.createDirectories(commitHistoryFile.parent)
  val json = commitHistory.toJson()
  Files.writeString(commitHistoryFile, json)
  Span.current().addEvent("write commit history", Attributes.of(AttributeKey.stringKey("data"), json))
  return commitHistoryFile
}

private suspend fun checkExists(
  connection: Http2ClientConnection,
  urlPath: String,
  logIfExists: Boolean = false,
): Boolean {
  val status = connection.head(urlPath)
  if (status == HttpResponseStatus.OK) {
    // already exists
    if (logIfExists) {
      Span.current().addEvent("File $urlPath already exists on server, nothing to upload")
    }
    return true
  }
  else if (status != HttpResponseStatus.NOT_FOUND) {
    Span.current().addEvent("unexpected response status for HEAD request", Attributes.of(AttributeKey.stringKey("status"), status.toString()))
  }
  return false
}