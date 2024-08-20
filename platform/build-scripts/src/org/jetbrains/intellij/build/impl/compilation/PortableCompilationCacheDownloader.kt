// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.platform.util.coroutines.mapConcurrent
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
import org.jetbrains.jps.cache.model.BuildTargetState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.LongAdder

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
        } else {
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

  private val blockingAvailableCachesKeys: Collection<String> by lazy {
    runBlocking {
      availableCachesKeysLazyTask.await()
    }
  }

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
    val failed = LongAdder()
    val totalDownloadedBytes = AtomicLong()
    var total = -1
    withContext(Dispatchers.IO) {
      launch {
        spanBuilder("get and unpack jps cache").setAttribute("commit", lastCachedCommit).use {
          saveJpsCache(lastCachedCommit, totalDownloadedBytes)
        }
      }

      val sourcesState = getSourcesState(lastCachedCommit)
      val outputs = sourcesStateProcessor.getAllCompilationOutputs(sourcesState)
      total = outputs.size
      spanBuilder("download compilation output parts").setAttribute(AttributeKey.longKey("count"), outputs.size.toLong()).use {
        outputs.mapConcurrent(downloadParallelism) { output ->
          spanBuilder("get and unpack output").setAttribute("part", output.remotePath).use { span ->
            try {
              saveOutput(compilationOutput = output, totalDownloadedBytes = totalDownloadedBytes, span = span)
            }
            catch (e: CancellationException) {
              throw e
            }
            catch (e: Throwable) {
              span.recordException(e)
              failed.increment()
            }
            null
          }
        }
      }
    }

    reportStatisticValue("jps-cache:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    reportStatisticValue("jps-cache:downloaded:bytes", "$totalDownloadedBytes")
    reportStatisticValue("jps-cache:downloaded:count", "${total - failed.sum()}")
    reportStatisticValue("jps-cache:failed:count", "${failed.sum()}")
  }

  private suspend fun getSourcesState(commitHash: String): Map<String, Map<String, BuildTargetState>> {
    return sourcesStateProcessor.parseSourcesStateFile(downloadString("$remoteCacheUrl/metadata/$commitHash"))
  }

  private suspend fun saveJpsCache(commitHash: String, totalBytes: AtomicLong) {
    var cacheArchive: Path? = null
    try {
      cacheArchive = downloadJpsCache(commitHash)

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
      if (cacheArchive != null) {
        Files.deleteIfExists(cacheArchive)
      }
    }
  }

  private suspend fun downloadJpsCache(commitHash: String): Path {
    val cacheArchive = Files.createTempFile("cache", ".zip")
    downloadToFile(url = "$remoteCacheUrl/caches/$commitHash", file = cacheArchive, spanName = "download jps cache")
    return cacheArchive
  }

  private suspend fun saveOutput(compilationOutput: CompilationOutput, totalDownloadedBytes: AtomicLong, span: Span) {
    var outputArchive: Path? = null
    try {
      outputArchive = downloadOutput(compilationOutput)
      totalDownloadedBytes.addAndGet(Files.size(outputArchive))
      spanBuilder("unpack output")
        .setAttribute("archive", "$outputArchive")
        .setAttribute("destination", compilationOutput.path)
        .use {
          Decompressor.Zip(outputArchive).overwrite(true).extract(Path.of(compilationOutput.path))
        }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      span.addEvent("cannot get output", Attributes.of(
        AttributeKey.stringKey("part"), compilationOutput.remotePath,
      ))
      throw Exception("Unable to decompress $remoteCacheUrl/${compilationOutput.remotePath} to ${compilationOutput.path}", e)
    }
    finally {
      if (outputArchive != null) {
        Files.deleteIfExists(outputArchive)
      }
    }
  }

  private suspend fun downloadOutput(compilationOutput: CompilationOutput): Path {
    val outputArchive = Path.of(compilationOutput.path, "tmp-output.zip")
    downloadToFile(url = "$remoteCacheUrl/${compilationOutput.remotePath}", file = outputArchive, spanName = "download output")
    return outputArchive
  }

  private fun reportStatisticValue(key: String, value: String) {
    context.messages.reportStatisticValue(key, value)
  }
}