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
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.ZstdCompressContextPool
import org.jetbrains.intellij.build.http2Client.upload
import org.jetbrains.intellij.build.http2Client.withHttp2ClientConnectionFactory
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.moveFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.jpsCache.*
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

private const val SOURCE_STATE_FILE_NAME = "target_sources_state.json"

/**
 * Upload local JPS Cache
 */
suspend fun uploadJpsCache(forceUpload: Boolean, context: CompilationContext) {
  uploadJpsCache(
    forceUpload = forceUpload,
    commitHash = jpsCacheCommit,
    s3Dir = jpsCacheS3Dir,
    authHeader = jpsCacheAuthHeader,
    uploadUrl = jpsCacheUploadUrl,
    jpsDataDir = context.compilationData.dataStorageRoot.toAbsolutePath().normalize(),
    classOutDir = context.classesOutputDirectory,
    tempDir = context.paths.tempDir,
    messages = context.messages,
  )
}

internal suspend fun uploadJpsCache(
  forceUpload: Boolean,
  commitHash: String,
  authHeader: CharSequence?,
  s3Dir: Path?,
  uploadUrl: URI,
  jpsDataDir: Path,
  classOutDir: Path,
  tempDir: Path,
  messages: BuildMessages,
) {
  if (s3Dir != null) {
    s3Dir.deleteRecursively()
    Files.createDirectories(s3Dir)
  }

  val sourceStateFile = jpsDataDir.resolve(SOURCE_STATE_FILE_NAME)
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
  val s3Time = LongAdder()
  withHttp2ClientConnectionFactory(trustAll = uploadUrl.host == "127.0.0.1") { client ->
    client.connect(address = uploadUrl, authHeader = authHeader).use { connection ->
      val urlPathPrefix = uploadUrl.path
      val zstdCompressContextPool = ZstdCompressContextPool()
      withContext(Dispatchers.IO) {
        launch {
          spanBuilder("upload JPS data").use {
            uploadJpsData(
              urlPathPrefix = urlPathPrefix,
              connection = connection,
              forceUpload = forceUpload,
              commitHash = commitHash,
              jpsDataDir = jpsDataDir,
              zstdCompressContextPool = zstdCompressContextPool,
            )
          }
          if (s3Dir != null) {
            spanBuilder("create JPS data archive for S3").use {
              val start = System.nanoTime()
              val zipFile = tempDir.resolve("$commitHash-${java.lang.Long.toUnsignedString(Random.nextLong(), Character.MAX_RADIX)}.zip")
              try {
                zipWithCompression(targetFile = zipFile, dirs = mapOf(jpsDataDir to ""), createFileParentDirs = false)
                moveFile(zipFile, s3Dir.resolve("caches/$commitHash"))
              }
              finally {
                Files.deleteIfExists(zipFile)
                s3Time.add(System.nanoTime() - start)
              }
            }
          }
        }

        spanBuilder("upload compilation outputs").use {
          val allCompilationOutputs = getAllCompilationOutputs(sourceState = sourceState, classOutDir = classOutDir)
          val zipTempDir = if (s3Dir == null) null else Files.createTempDirectory(tempDir, "jps-cache-bytecode-")
          try {
            allCompilationOutputs.forEachConcurrent(uploadParallelism) { compilationOutput ->
              uploadCompilationOutput(
                uploadedOutputCount = uploadedOutputCount,
                totalUploadedBytes = totalUploadedBytes,
                compilationOutput = compilationOutput,
                urlPathPrefix = urlPathPrefix,
                connection = connection,
                s3Dir = s3Dir,
                forcedUpload = forceUpload,
                tempDir = zipTempDir,
                zstdCompressContextPool = zstdCompressContextPool,
              )
            }
          }
          finally {
            zipTempDir?.deleteRecursively()
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

  val s3ElapsedTime = s3Time.sum()
  messages.reportStatisticValue("jps-cache:upload:time", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start - s3ElapsedTime).toString())
  messages.reportStatisticValue("jps-cache:upload-s3:time", TimeUnit.NANOSECONDS.toMillis(s3ElapsedTime).toString())
  messages.reportStatisticValue("jps-cache:uploaded:count", (uploadedOutputCount.sum() + 1).toString())
  messages.reportStatisticValue("jps-cache:uploaded:bytes", totalUploadedBytes.sum().toString())

  messages.reportStatisticValue("Uploaded outputs", uploadedOutputCount.sum().toString())

  if (s3Dir != null) {
    uploadToS3(s3Dir)
  }
}

private suspend fun uploadToS3(s3Dir: Path) {
  spanBuilder("aws s3 cp").use {
    awsS3Cli("cp", "--no-progress", "--include", "*", "--recursive", s3Dir.toString(), "s3://intellij-jps-cache", returnStdOut = false)
  }
}

private suspend fun uploadJpsData(
  urlPathPrefix: String,
  connection: Http2ClientConnection,
  commitHash: String,
  forceUpload: Boolean,
  jpsDataDir: Path,
  zstdCompressContextPool: ZstdCompressContextPool,
) {
  val urlPath = "$urlPathPrefix/caches/$commitHash.zip.zstd"
  if (forceUpload || !checkExists(connection = connection, urlPath = urlPath, logIfExists = true)) {
    // Level 9 is optimal, the same as for compilation parts. Levels beyond 9 consume more time without a significant reduction in size
    connection.upload(path = urlPath, file = jpsDataDir, zstdCompressContextPool = zstdCompressContextPool, isDir = true)
  }
}

private suspend fun uploadCompilationOutput(
  uploadedOutputCount: LongAdder,
  compilationOutput: CompilationOutput,
  urlPathPrefix: String,
  connection: Http2ClientConnection,
  totalUploadedBytes: LongAdder,
  forcedUpload: Boolean,
  tempDir: Path?,
  s3Dir: Path?,
  zstdCompressContextPool: ZstdCompressContextPool,
) {
  val outDir = compilationOutput.path
  val sourcePath = compilationOutput.remotePath
  val urlPath = "$urlPathPrefix/$sourcePath.zip.zstd"
  spanBuilder("upload output part")
    .setAttribute("part", compilationOutput.remotePath)
    .setAttribute("path", outDir.toString())
    .setAttribute("urlPath", urlPath)
    .use { span ->
      if (Files.notExists(outDir)) {
        span.addEvent("doesn't exist, was a respective module removed?")
        return@use
      }

      if (forcedUpload || !checkExists(connection, urlPath)) {
        spanBuilder("upload").setAttribute("urlPath", urlPath).setAttribute("path", sourcePath).use {
          val result = connection.upload(path = urlPath, file = outDir, isDir = true, zstdCompressContextPool = zstdCompressContextPool)
          totalUploadedBytes.add(result.uploadedSize)
        }
        uploadedOutputCount.increment()
      }
    }

  if (s3Dir != null) {
    spanBuilder("create output part for S3").setAttribute("path", sourcePath).use {
      val zipFile = tempDir!!.resolve("${sourcePath.replace('/', '_')}.zip")
      zipWithCompression(targetFile = zipFile, dirs = mapOf(outDir to ""), createFileParentDirs = false)
      moveFile(zipFile, s3Dir.resolve(sourcePath))
    }
  }
}

internal suspend fun checkExists(
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