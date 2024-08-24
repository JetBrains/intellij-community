// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.platform.util.coroutines.forEachConcurrent
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

private const val COMMITS_COUNT = 1_000

internal class PortableCompilationCacheDownloader(
  private val context: CompilationContext,
  private val git: Git,
  private val remoteCache: PortableCompilationCache.RemoteCache,
  private val gitUrl: String,
) {
  private val remoteCacheUrl = remoteCache.url.trimEnd('/')

  private val sourcesStateProcessor = SourcesStateProcessor(context.compilationData.dataStorageRoot, context.classesOutputDirectory)

  private val lastCommits by lazy { git.log(COMMITS_COUNT) }

  private suspend fun downloadString(url: String): String = retryWithExponentialBackOff {
    if (url.isS3()) {
      awsS3Cli("cp", url, "-")
    }
    else {
      httpClient.get(url, remoteCache.authHeader) { it.body.string() }
    }
  }

  private suspend fun downloadToFile(url: String, file: Path, spanName: String) {
    spanBuilder(spanName).setAttribute("url", url).setAttribute("path", "$file").use {
      Files.createDirectories(file.parent)
      retryWithExponentialBackOff {
        if (url.isS3()) {
          awsS3Cli("cp", url, "$file")
        }
        else {
          httpClient.get(url, remoteCache.authHeader) { response ->
            Files.newOutputStream(file).use {
              response.body.byteStream().transferTo(it)
            }
          }
        }
      }
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val availableCachesKeysLazyTask = GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
    val commitHistory = "$remoteCacheUrl/${CommitsHistory.JSON_FILE}"
    if (isExist(commitHistory)) {
      val json = downloadString(commitHistory)
      CommitsHistory(json).commitsForRemote(gitUrl)
    }
    else {
      emptyList()
    }
  }

  @OptIn(DelicateCoroutinesApi::class)
  private val availableCommitDepthLazyTask = GlobalScope.async(Dispatchers.Unconfined, start = CoroutineStart.LAZY) {
    val availableCachesKeys = availableCachesKeysLazyTask.await()
    lastCommits.indexOfFirst {
      availableCachesKeys.contains(it)
    }
  }

  suspend fun getAvailableCommitDepth(): Int = availableCommitDepthLazyTask.await()

  private suspend fun isExist(path: String): Boolean {
    return spanBuilder("head").setAttribute("url", remoteCacheUrl).use {
      retryWithExponentialBackOff {
        httpClient.head(path, remoteCache.authHeader) == 200
      }
    }
  }

  suspend fun download() {
    val availableCommitDepth = getAvailableCommitDepth()
    if (availableCommitDepth !in 0 until lastCommits.count()) {
      Span.current().addEvent("Unable to find cache for any of last ${lastCommits.count()} commits.")
      return
    }

    val lastCachedCommit = lastCommits.get(availableCommitDepth)
    Span.current().addEvent(
      "using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit)",
      Attributes.of(
        AttributeKey.longKey("behind last commit"), availableCommitDepth.toLong(),
        AttributeKey.stringArrayKey("available cache keys"), availableCachesKeysLazyTask.await().toList(),
      )
    )
    val start = System.nanoTime()
    val totalDownloadedBytes = AtomicLong()
    var total = -1
    withContext(Dispatchers.IO) {
      launch {
        spanBuilder("get and unpack jps cache").setAttribute("commit", lastCachedCommit).use {
          downloadAndUnpackJpsCache(lastCachedCommit, totalDownloadedBytes)
        }
      }

      val sourcesState = sourcesStateProcessor.parseSourcesStateFile(downloadString("$remoteCacheUrl/metadata/$lastCachedCommit"))
      val outputs = sourcesStateProcessor.getAllCompilationOutputs(sourcesState)
      total = outputs.size
      spanBuilder("download compilation output parts").setAttribute(AttributeKey.longKey("count"), outputs.size.toLong()).use {
        outputs.forEachConcurrent(downloadParallelism) { output ->
          spanBuilder("get and unpack output").setAttribute("part", output.remotePath).use {
            downloadAndUnpackCompilationOutput(compilationOutput = output, totalDownloadedBytes = totalDownloadedBytes)
          }
        }
      }
    }

    reportStatisticValue("jps-cache:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    reportStatisticValue("jps-cache:downloaded:bytes", totalDownloadedBytes.toString())
    reportStatisticValue("jps-cache:downloaded:count", total.toString())
  }

  private suspend fun downloadAndUnpackJpsCache(commitHash: String, totalBytes: AtomicLong) {
    val cacheArchive = Files.createTempFile("cache", ".zip")
    try {
      downloadToFile(url = "$remoteCacheUrl/caches/$commitHash", file = cacheArchive, spanName = "download jps cache")

      totalBytes.addAndGet(Files.size(cacheArchive))
      val cacheDestination = context.compilationData.dataStorageRoot

      spanBuilder("unpack jps cache")
        .setAttribute("archive", "$cacheArchive")
        .setAttribute("destination", "$cacheDestination")
        .use {
          Decompressor.Zip(cacheArchive).overwrite(true).extract(cacheDestination)
        }
    }
    finally {
      Files.deleteIfExists(cacheArchive)
    }
  }

  private suspend fun downloadAndUnpackCompilationOutput(compilationOutput: CompilationOutput, totalDownloadedBytes: AtomicLong) {
    val tempFile = compilationOutput.path.resolve("tmp-output.zip")
    downloadToFile(url = "$remoteCacheUrl/${compilationOutput.remotePath}", file = tempFile, spanName = "download output")
    totalDownloadedBytes.addAndGet(Files.size(tempFile))

    try {
      spanBuilder("unpack output")
        .setAttribute("archive", tempFile.toString())
        .setAttribute("destination", compilationOutput.path.toString())
        .use {
          Decompressor.Zip(tempFile).overwrite(true).extract(compilationOutput.path)
        }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      throw Exception("Unable to unpack $remoteCacheUrl/${compilationOutput.remotePath} to ${compilationOutput.path}", e)
    }
    finally {
      Files.deleteIfExists(tempFile)
    }
  }

  private fun reportStatisticValue(key: String, value: String) {
    context.messages.reportStatisticValue(key, value)
  }
}