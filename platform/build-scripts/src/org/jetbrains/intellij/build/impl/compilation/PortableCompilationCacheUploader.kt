// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.diagnostic.telemetry.use
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.util.io.Compressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import okio.source
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.io.moveFile
import org.jetbrains.intellij.build.io.retryWithExponentialBackOff
import org.jetbrains.intellij.build.io.zip
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

internal class PortableCompilationCacheUploader(
  private val context: CompilationContext,
  remoteCacheUrl: String,
  private val remoteGitUrl: String,
  private val commitHash: String,
  private val syncFolder: String,
  private val uploadCompilationOutputsOnly: Boolean,
  private val forcedUpload: Boolean,
) {
  private val uploadedOutputCount = AtomicInteger()

  private val sourcesStateProcessor = SourcesStateProcessor(context.compilationData.dataStorageRoot, context.paths.buildOutputDir)
  private val uploader = Uploader(remoteCacheUrl)

  private val commitHistory = CommitsHistory(mapOf(remoteGitUrl to setOf(commitHash)))

  fun upload(messages: BuildMessages) {
    if (!Files.exists(sourcesStateProcessor.sourceStateFile)) {
      messages.warning("Compilation outputs doesn't contain source state file, " +
                       "please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag")
      return
    }

    val start = System.nanoTime()
    val tasks = mutableListOf<ForkJoinTask<*>>()
    if (!uploadCompilationOutputsOnly) {
      // Jps Caches upload is started first because of significant size
      tasks.add(ForkJoinTask.adapt(::uploadJpsCaches))
    }

    val currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()
    uploadCompilationOutputs(currentSourcesState, uploader, tasks)
    ForkJoinTask.invokeAll(tasks)

    messages.reportStatisticValue("Compilation upload time, ms", (TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start).toString()))
    val totalOutputs = (sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).size).toString()
    messages.reportStatisticValue("Total outputs", totalOutputs)
    messages.reportStatisticValue("Uploaded outputs", uploadedOutputCount.get().toString())

    uploadMetadata()
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
    moveFile(zipFile, Path.of(syncFolder, cachePath))
  }

  private fun uploadMetadata() {
    val metadataPath = "metadata/$commitHash"
    val sourceStateFile = sourcesStateProcessor.sourceStateFile
    uploader.upload(metadataPath, sourceStateFile)
    val sourceStateFileCopy = Path.of(syncFolder, metadataPath)
    moveFile(sourceStateFile, sourceStateFileCopy)
  }

  private fun uploadCompilationOutputs(currentSourcesState: Map<String, Map<String, BuildTargetState>>,
                                       uploader: Uploader,
                                       tasks: MutableList<ForkJoinTask<*>>): List<ForkJoinTask<*>> {
    return sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).mapTo(tasks) { compilationOutput ->
      ForkJoinTask.adapt {
        val sourcePath = compilationOutput.remotePath
        val outputFolder = Path.of(compilationOutput.path)
        if (!Files.exists(outputFolder)) {
          Span.current().addEvent("$outputFolder doesn't exist, was a respective module removed?")
          return@adapt
        }

        val zipFile = outputFolder.parent.resolve(compilationOutput.hash)
        zip(zipFile, mapOf(outputFolder to ""), compress = true)
        if (!uploader.isExist(sourcePath)) {
          uploader.upload(sourcePath, zipFile)
          uploadedOutputCount.incrementAndGet()
        }
        moveFile(zipFile, Path.of(syncFolder, sourcePath))
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
    uploader.upload(path = CommitsHistory.JSON_FILE,
                    file = writeCommitHistory(if (overrideRemoteHistory) commitHistory else commitHistory.plus(remoteCommitHistory())))
  }

  private fun remoteCommitHistory(): CommitsHistory {
    return if (uploader.isExist(CommitsHistory.JSON_FILE)) {
      CommitsHistory(uploader.getAsString(CommitsHistory.JSON_FILE))
    }
    else {
      CommitsHistory(emptyMap())
    }
  }

  private fun writeCommitHistory(commitHistory: CommitsHistory): Path {
    val commitHistoryFile = Path.of(syncFolder, CommitsHistory.JSON_FILE)
    Files.createDirectories(commitHistoryFile.parent)
    val json = commitHistory.toJson()
    Files.writeString(commitHistoryFile, json)
    Span.current().addEvent("write commit history", Attributes.of(AttributeKey.stringKey("data"), json))
    return commitHistoryFile
  }
}

private class Uploader(serverUrl: String) {
  private val serverUrl = toUrlWithTrailingSlash(serverUrl)

  fun upload(path: String, file: Path): Boolean {
    val url = pathToUrl(path)
    spanBuilder("upload").setAttribute("url", url).setAttribute("path", path).useWithScope {
      check(Files.exists(file)) {
        "The file $file does not exist"
      }

      val call = httpClient.newCall(Request.Builder().url(url).put(object : RequestBody() {
        override fun contentType() = MEDIA_TYPE_BINARY

        override fun contentLength() = Files.size(file)

        override fun writeTo(sink: BufferedSink) {
          file.source().use(sink::writeAll)
        }
      }).build())
      retryWithExponentialBackOff { call.execute().useSuccessful {} }
    }
    return true
  }

  fun isExist(path: String, logIfExists: Boolean = false): Boolean {
    val url = pathToUrl(path)
    spanBuilder("head").setAttribute("url", url).use { span ->
      val code = retryWithExponentialBackOff {
        httpClient.head(url).use { it.code }
      }
      if (code == 200) {
        if (logIfExists) {
          span.addEvent("File '$path' already exists on server, nothing to upload")
        }
        return true
      }
      check(code == 404) {
        "HEAD $url responded with unexpected $code"
      }
    }
    return false
  }

  fun getAsString(path: String) = retryWithExponentialBackOff {
    httpClient.get(pathToUrl(path)).useSuccessful { it.body.string() }
  }

  private fun pathToUrl(path: String) = "$serverUrl${path.trimStart('/')}"
}