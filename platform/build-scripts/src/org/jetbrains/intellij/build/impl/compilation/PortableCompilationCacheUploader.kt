// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.FileUtil
import com.intellij.platform.diagnostic.telemetry.helpers.use
import com.intellij.platform.diagnostic.telemetry.helpers.useWithoutActiveScope
import com.intellij.util.io.Compressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.impl.withTrailingSlash
import org.jetbrains.intellij.build.io.copyFile
import org.jetbrains.intellij.build.io.moveFile
import org.jetbrains.intellij.build.io.zipWithCompression
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.intellij.build.suspendingRetryWithExponentialBackOff
import org.jetbrains.jps.cache.model.BuildTargetState
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

internal class PortableCompilationCacheUploader(
  private val context: CompilationContext,
  private val remoteCache: PortableCompilationCache.RemoteCache,
  private val remoteGitUrl: String,
  private val commitHash: String,
  s3Folder: String,
  private val forcedUpload: Boolean,
) {
  private val uploadedOutputCount = AtomicInteger()

  private val sourcesStateProcessor = SourcesStateProcessor(context.compilationData.dataStorageRoot, context.classesOutputDirectory)
  private val uploader = Uploader(remoteCache.uploadUrl, remoteCache.authHeader)

  private val commitHistory = CommitsHistory(mapOf(remoteGitUrl to setOf(commitHash)))

  private val s3Folder = Path.of(s3Folder)

  init {
    FileUtil.delete(this.s3Folder)
    Files.createDirectories(this.s3Folder)
  }

  fun upload(messages: BuildMessages) {
    check(Files.exists(sourcesStateProcessor.sourceStateFile)) {
      "Compilation outputs doesn't contain source state file, " +
      "please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag"
    }

    val start = System.nanoTime()
    val totalUploadedBytes = AtomicLong()
    val tasks = mutableListOf<ForkJoinTask<*>>()
    // Jps Caches upload is started first because of significant size
    tasks.add(forkJoinTask(spanBuilder("upload jps cache")) { uploadJpsCaches() })

    val currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()
    uploadCompilationOutputs(currentSourcesState, uploader, tasks)
    val failed = ForkJoinTask.invokeAll(tasks).mapNotNull { it.exception }

    messages.reportStatisticValue("jps-cache:upload:time", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toString())
    messages.reportStatisticValue("jps-cache:uploaded:count", "${tasks.size - failed.size}")
    messages.reportStatisticValue("jps-cache:uploaded:bytes", "$totalUploadedBytes")

    val totalOutputs = (sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).size).toString()
    messages.reportStatisticValue("Total outputs", totalOutputs)
    messages.reportStatisticValue("Uploaded outputs", uploadedOutputCount.get().toString())

    uploadMetadata()
    uploadToS3()
  }

  private fun uploadToS3() {
    spanBuilder("aws s3 sync").use {
      awsS3Cli("cp", "--no-progress", "--include", "*", "--recursive", "$s3Folder", "s3://intellij-jps-cache", returnStdOut = false)
    }
  }

  private fun uploadJpsCaches() {
    val dataStorageRoot = context.compilationData.dataStorageRoot
    val zipFile = dataStorageRoot.parent.resolve(commitHash)
    Compressor.Zip(zipFile).use { zip ->
      zip.addDirectory(dataStorageRoot)
    }
    val cachePath = "caches/$commitHash"
    if (forcedUpload || !uploader.isExist(cachePath, true)) {
      uploader.upload(cachePath, zipFile)
    }
    moveFile(zipFile, s3Folder.resolve(cachePath))
  }

  private fun uploadMetadata() {
    val metadataPath = "metadata/$commitHash"
    spanBuilder("upload metadata").setAttribute("path", metadataPath).use {
      val sourceStateFile = sourcesStateProcessor.sourceStateFile
      uploader.upload(metadataPath, sourceStateFile)
      copyFile(sourceStateFile, s3Folder.resolve(metadataPath))
    }
  }

  private fun uploadCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>,
                                       uploader: Uploader,
                                       tasks: MutableList<ForkJoinTask<*>>): List<ForkJoinTask<*>> {
    return sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).mapTo(tasks) { compilationOutput ->
      forkJoinTask(spanBuilder("upload output part").setAttribute("part", compilationOutput.remotePath)) { span ->
        val sourcePath = compilationOutput.remotePath
        val outputFolder = Path.of(compilationOutput.path)
        if (!Files.exists(outputFolder)) {
          span.addEvent("$outputFolder doesn't exist, was a respective module removed?", Attributes.of(
            AttributeKey.stringKey("path"), "$outputFolder"
          ))
          return@forkJoinTask
        }

        val zipFile = context.paths.tempDir.resolve("compilation-output-zips").resolve(sourcePath)
        zipWithCompression(zipFile, mapOf(outputFolder to ""))
        if (forcedUpload || !uploader.isExist(sourcePath)) {
          uploader.upload(sourcePath, zipFile)
          uploadedOutputCount.incrementAndGet()
        }
        moveFile(zipFile, s3Folder.resolve(sourcePath))
      }
    }
  }

  /**
   * Upload and publish file with commits history
   */
  fun updateCommitHistory(commitHistory: CommitsHistory = this.commitHistory, overrideRemoteHistory: Boolean = false) {
    for (commitHash in commitHistory.commitsForRemote(remoteGitUrl)) {
      val cacheUploaded = uploader.isExist("caches/$commitHash")
      val metadataUploaded = uploader.isExist("metadata/$commitHash")
      if (!cacheUploaded && !metadataUploaded) {
        val msg = "Unable to publish $commitHash due to missing caches/$commitHash and metadata/$commitHash. " +
                  "Probably caused by previous cleanup build."
        if (overrideRemoteHistory) context.messages.error(msg) else context.messages.warning(msg)
        return
      }
      check(cacheUploaded == metadataUploaded) {
        "JPS Caches are uploaded: $cacheUploaded, metadata is uploaded: $metadataUploaded"
      }
    }
    val newHistory: CommitsHistory = uploadHistory(overrideRemoteHistory, commitHistory)
    val expected = newHistory.commitsForRemote(remoteGitUrl).toSet()
    val actual = remoteCommitHistory().first.commitsForRemote(remoteGitUrl).toSet()
    val missing = expected - actual
    val unexpected = actual - expected
    check(missing.none() && unexpected.none()) {
      """
        Missing: $missing
        Unexpected: $unexpected
      """.trimIndent()
    }
  }

  private fun uploadHistory(
    overrideRemoteHistory: Boolean,
    commitHistory: CommitsHistory,
  ): CommitsHistory {
    if (overrideRemoteHistory) {
      val updated = commitHistory
      uploader.upload(path = CommitsHistory.JSON_FILE, file = writeCommitHistory(updated))
      return updated
    }
    else {
      var attempts = 0
      while (true) {
        val (remoteCommitHistory, etag) = remoteCommitHistory()
        val updated = commitHistory + remoteCommitHistory
        try {
          val content: ByteArray = updated.toJson().toByteArray(Charsets.UTF_8)
          runBlocking(Dispatchers.IO) {
            // Retry all errors except 409 and 412, we handle them below in our own loop
            suspendingRetryWithExponentialBackOff(onException = { _: Int, e: Exception -> if (e is HttpException && e.code in setOf(409, 412)) throw e }) {
              uploader.uploadContent(
                path = CommitsHistory.JSON_FILE,
                content = content,
                contentType = "application/json; charset=utf-8".toMediaType(),
                headers = etag?.let { listOf("If-Match" to etag.removePrefix("W/")) } ?: emptyList(),
              )
            }
          }
        }
        catch (e: HttpException) {
          if (e.code == 409 || e.code == 412) {
            if (attempts > 10) {
              Span.current().addEvent("upload failed with etag mismatch, attempts limit reached")
              throw e
            }
            Span.current().addEvent("upload failed with etag mismatch, will try again")
            attempts++
            continue
          }
        }
        writeCommitHistory(updated)
        return updated
      }
    }
  }

  private fun remoteCommitHistory(): Pair<CommitsHistory, String?> {
    return if (uploader.isExist(CommitsHistory.JSON_FILE)) {
      val response = uploader.getAsStringAndHeaders(CommitsHistory.JSON_FILE, remoteCache.authHeader)
      CommitsHistory(response.first) to response.second["ETag"]
    }
    else {
      CommitsHistory(emptyMap()) to null
    }
  }

  private fun writeCommitHistory(commitHistory: CommitsHistory): Path {
    val commitHistoryFile = s3Folder.resolve(CommitsHistory.JSON_FILE)
    Files.createDirectories(commitHistoryFile.parent)
    val json = commitHistory.toJson()
    Files.writeString(commitHistoryFile, json)
    Span.current().addEvent("write commit history", Attributes.of(AttributeKey.stringKey("data"), json))
    return commitHistoryFile
  }
}

private class Uploader(serverUrl: String, val authHeader: String) {
  private val serverUrl = serverUrl.withTrailingSlash()

  fun upload(path: String, file: Path) {
    val url = pathToUrl(path)
    spanBuilder("upload").setAttribute("url", url).setAttribute("path", path).use {
      check(Files.exists(file)) {
        "The file $file does not exist"
      }
      retryWithExponentialBackOff {
        httpClient.newCall(Request.Builder().url(url)
          .header("Authorization", authHeader)
          .put(object : RequestBody() {
            override fun contentType() = MEDIA_TYPE_BINARY

            override fun contentLength() = Files.size(file)

            override fun writeTo(sink: BufferedSink) {
              file.source().use(sink::writeAll)
            }
          }).build()).execute().useSuccessful {}
      }
    }
  }

  fun uploadContent(path: String, content: ByteArray, contentType: MediaType, headers: List<Pair<String, String>> = emptyList()) {
    val url = pathToUrl(path)
    spanBuilder("upload")
      .setAttribute("url", url).setAttribute("content-length", content.size.toString())
      .apply { headers.forEach { (name, value) -> setAttribute("header:$name", value) } }
      .use {
        httpClient.newCall(
          Request.Builder().url(url)
            .header("Authorization", authHeader)
            .apply { headers.forEach { (name, value) -> header(name, value) } }
            .put(object : RequestBody() {
              override fun contentType() = contentType

              override fun contentLength() = content.size.toLong()

              override fun writeTo(sink: BufferedSink) {
                sink.write(content)
              }
            }).build()
        ).execute().useSuccessful {}
      }
  }

  fun isExist(path: String, logIfExists: Boolean = false): Boolean {
    val url = pathToUrl(path)
    spanBuilder("head").setAttribute("url", url).useWithoutActiveScope { span ->
      val code = retryWithExponentialBackOff {
        httpClient.head(url, authHeader)
      }
      if (code == 200) {
        try {
          /**
           * FIXME dirty workaround for unreliable [serverUrl]
           */
          httpClient.get(url, authHeader) {
            it.peekBody(byteCount = 1)
          }
        }
        catch (ignored: Exception) {
          return false
        }
        if (logIfExists) {
          span.addEvent("File '$path' already exists on server, nothing to upload")
        }
        return true
      }
    }
    return false
  }

  fun getAsStringAndHeaders(path: String, authHeader: String): Pair<String, Headers> {
    val url = pathToUrl(path)
    spanBuilder("get").setAttribute("url", url).useWithoutActiveScope {
      return retryWithExponentialBackOff {
        httpClient.get(url, authHeader) { it.body.string() to it.headers }
      }
    }
  }

  private fun pathToUrl(path: String) = "$serverUrl${path.trimStart('/')}"
}