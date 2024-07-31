// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import org.jetbrains.intellij.build.telemetry.use
import com.intellij.util.io.Decompressor
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.telemetry.TraceManager
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.jps.cache.model.BuildTargetState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask
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

  private fun downloadString(url: String): String = retryWithExponentialBackOff {
    if (url.isS3()) {
      awsS3Cli("cp", url, "-")
    }
    else {
      httpClient.get(url, remoteCache.authHeader) { it.body.string() }
    }
  }

  private fun downloadToFile(url: String, file: Path, spanName: String) {
    TraceManager.spanBuilder(spanName).setAttribute("url", url).setAttribute("path", "$file").use {
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

  val availableCommitDepth by lazy {
    lastCommits.indexOfFirst {
      availableCachesKeys.contains(it)
    }
  }

  private val availableCachesKeys by lazy {
    val commitHistory = "$remoteCacheUrl/${CommitsHistory.JSON_FILE}"
    if (isExist(commitHistory)) {
      val json = downloadString(commitHistory)
      CommitsHistory(json).commitsForRemote(gitUrl)
    }
    else {
      emptyList()
    }
  }

  private fun isExist(path: String): Boolean =
    TraceManager.spanBuilder("head").setAttribute("url", remoteCacheUrl).use {
      retryWithExponentialBackOff {
        httpClient.head(path, remoteCache.authHeader) == 200
      }
    }

  fun download() {
    if (availableCommitDepth in 0 until lastCommits.count()) {
      val lastCachedCommit = lastCommits.get(availableCommitDepth)
      context.messages.info("Using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit).")
      context.messages.block("Available cache keys listing") {
        availableCachesKeys.forEach(context.messages::info)
      }
      val start = System.nanoTime()
      val tasks = mutableListOf<ForkJoinTask<*>>()
      val totalDownloadedBytes = AtomicLong()
      tasks.add(forkJoinTask(TraceManager.spanBuilder("get and unpack jps cache").setAttribute("commit", lastCachedCommit)) {
        saveJpsCache(lastCachedCommit, totalDownloadedBytes)
      })

      val sourcesState = getSourcesState(lastCachedCommit)
      val outputs = sourcesStateProcessor.getAllCompilationOutputs(sourcesState)
      context.messages.info("Going to download ${outputs.size} compilation output parts.")
      outputs.forEach { output ->
        tasks.add(forkJoinTask(TraceManager.spanBuilder("get and unpack output").setAttribute("part", output.remotePath)) { span ->
          saveOutput(output, totalDownloadedBytes, span)
        })
      }

      val failed = ForkJoinTask.invokeAll(tasks).mapNotNull { it.exception }
      reportStatisticValue("jps-cache:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
      reportStatisticValue("jps-cache:downloaded:bytes", "$totalDownloadedBytes")
      reportStatisticValue("jps-cache:downloaded:count", "${tasks.size - failed.size}")
      reportStatisticValue("jps-cache:failed:count", "${failed.size}")
    }
    else {
      context.messages.warning("Unable to find cache for any of last ${lastCommits.count()} commits.")
    }
  }

  private fun getSourcesState(commitHash: String): Map<String, Map<String, BuildTargetState>> {
    return sourcesStateProcessor.parseSourcesStateFile(downloadString("$remoteCacheUrl/metadata/$commitHash"))
  }

  private fun saveJpsCache(commitHash: String, totalBytes: AtomicLong) {
    var cacheArchive: Path? = null
    try {
      cacheArchive = downloadJpsCache(commitHash)

      totalBytes.addAndGet(Files.size(cacheArchive))
      val cacheDestination = context.compilationData.dataStorageRoot

      TraceManager.spanBuilder("unpack jps cache")
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

  private fun downloadJpsCache(commitHash: String): Path {
    val cacheArchive = Files.createTempFile("cache", ".zip")
    downloadToFile("$remoteCacheUrl/caches/$commitHash", cacheArchive, spanName = "download jps cache")
    return cacheArchive
  }

  private fun saveOutput(compilationOutput: CompilationOutput, totalDownloadedBytes: AtomicLong, span: Span) {
    var outputArchive: Path? = null
    try {
      outputArchive = downloadOutput(compilationOutput)
      totalDownloadedBytes.addAndGet(Files.size(outputArchive))
      TraceManager.spanBuilder("unpack output")
        .setAttribute("archive", "$outputArchive")
        .setAttribute("destination", compilationOutput.path)
        .use {
          Decompressor.Zip(outputArchive).overwrite(true).extract(Path.of(compilationOutput.path))
        }
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

  private fun downloadOutput(compilationOutput: CompilationOutput): Path {
    val outputArchive = Path.of(compilationOutput.path, "tmp-output.zip")
    downloadToFile("$remoteCacheUrl/${compilationOutput.remotePath}", outputArchive, spanName = "download output")
    return outputArchive
  }

  private fun reportStatisticValue(key: String, value: String) {
    context.messages.reportStatisticValue(key, value)
  }
}