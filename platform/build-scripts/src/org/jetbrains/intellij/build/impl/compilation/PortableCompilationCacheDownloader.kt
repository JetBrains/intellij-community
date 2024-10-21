// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.stream.JsonReader
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.*
import org.jetbrains.intellij.build.jpsCache.*
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.intellij.build.telemetry.withTracer
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

private const val COMMITS_COUNT = 1_000

internal object TestJpsCacheDownload {
  @ExperimentalPathApi
  @JvmStatic
  fun main(args: Array<String>) = withTracer(serviceName = "test-jps-cache-downloader") {
    System.setProperty("jps.cache.test", "true")
    System.setProperty("org.jetbrains.jps.portable.caches", "true")

    val projectHome = ULTIMATE_HOME
    val outputDir = projectHome.resolve("out/test-jps-cache-downloaded")
    outputDir.deleteRecursively()
    downloadJpsCache(
      cacheUrl = getJpsCacheUrl("https://127.0.0.1:1900/cache/jps"),
      gitUrl = jpsCacheRemoteGitUrl,
      authHeader = jpsCacheAuthHeader,
      projectHome = projectHome,
      classOutDir = outputDir.resolve("classes"),
      cacheDestination = outputDir.resolve("jps-build-data"),
      reportStatisticValue = { k, v ->
        println("$k: $v")
      }
    )
  }
}

internal suspend fun downloadJpsCache(
  cacheUrl: URI,
  authHeader: CharSequence?,
  gitUrl: String,
  projectHome: Path,
  classOutDir: Path,
  cacheDestination: Path,
  reportStatisticValue: (key: String, value: String) -> Unit,
): Int {
  val start = System.nanoTime()
  val totalDownloadedBytes = LongAdder()
  val notFound = LongAdder()
  var availableCommitDepth = -1
  val totalItemCount = withHttp2ClientConnectionFactory(trustAll = cacheUrl.host == "127.0.0.1") { client ->
    checkMirrorAndConnect(initialServerUri = cacheUrl, client = client, authHeader = authHeader) { connection, urlPathPrefix ->
      val info = spanBuilder("prepare downloading").use {
        prepareDownload(urlPathPrefix = urlPathPrefix, gitUrl = gitUrl, connection = connection, lastCommits = Git(projectHome).log(COMMITS_COUNT))
      } ?: return@checkMirrorAndConnect -1
      availableCommitDepth = info.second
      spanBuilder("download JPS Cache").setAttribute("commit", info.first).use {
        doDownload(
          urlPathPrefix = urlPathPrefix,
          lastCachedCommit = info.first,
          notFound = notFound,
          totalDownloadedBytes = totalDownloadedBytes,
          connection = connection,
          classOutDir = classOutDir,
          cacheDestination = cacheDestination,
        )
      }
    }
  }

  if (availableCommitDepth == -1) {
    return -1
  }

  reportStatisticValue("jps-cache:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
  reportStatisticValue("jps-cache:downloaded:bytes", totalDownloadedBytes.sum().toString())
  reportStatisticValue("jps-cache:downloaded:count", totalItemCount.toString())
  reportStatisticValue("jps-cache:notFound:count", notFound.sum().toString())

  return availableCommitDepth
}

private suspend fun prepareDownload(urlPathPrefix: String, gitUrl: String, connection: Http2ClientConnection, lastCommits: List<String>): Pair<String, Int>? {
  val availableCachesKeys = getAvailableCachesKeys(urlPathPrefix = urlPathPrefix, gitUrl = gitUrl, connection = connection)
  val availableCommitDepth = lastCommits.indexOfFirst {
    availableCachesKeys.contains(it)
  }

  if (availableCommitDepth !in 0 until lastCommits.count()) {
    Span.current().addEvent(
      "unable to find cache for any of last ${lastCommits.count()} commits",
      Attributes.of(
        AttributeKey.stringArrayKey("availableCacheKeys"), availableCachesKeys.toList()
      ),
    )
    return null
  }

  val lastCachedCommit = lastCommits.get(availableCommitDepth)
  Span.current().addEvent(
    "using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit)",
    Attributes.of(
      AttributeKey.longKey("behind last commit"), availableCommitDepth.toLong(),
      AttributeKey.stringArrayKey("available cache keys"), availableCachesKeys.toList(),
    )
  )
  return lastCachedCommit to availableCommitDepth
}

private suspend fun getAvailableCachesKeys(urlPathPrefix: String, gitUrl: String, connection: Http2ClientConnection): Collection<String> {
  val commitHistoryUrl = "$urlPathPrefix/$COMMIT_HISTORY_JSON_FILE"
  val data: Map<String, Set<String>> = connection.getJsonOrDefaultIfNotFound(path = commitHistoryUrl, defaultIfNotFound = emptyMap())
  if (data.isEmpty()) {
    return emptyList()
  }

  val result = data.get(gitUrl) ?: emptyList()
  if (result.isEmpty()) {
    Span.current().addEvent(
      "no data for remote",
      Attributes.of(
        AttributeKey.stringKey("remote"), gitUrl,
        AttributeKey.stringArrayKey("availableRemotes"), java.util.List.copyOf(data.keys),
      ),
    )
  }
  return result
}

private suspend fun doDownload(
  urlPathPrefix: String,
  lastCachedCommit: String,
  notFound: LongAdder,
  totalDownloadedBytes: LongAdder,
  connection: Http2ClientConnection,
  classOutDir: Path,
  cacheDestination: Path,
): Int {
  return withContext(Dispatchers.IO) {
    val zstdDecompressContextPool = ZstdDecompressContextPool()
    launch(CoroutineName("download JPS Cache")) {
      downloadAndUnpackJpsCache(
        urlPathPrefix = urlPathPrefix,
        commitHash = lastCachedCommit,
        totalBytes = totalDownloadedBytes,
        connection = connection,
        cacheDestination = cacheDestination,
        zstdDecompressContextPool = zstdDecompressContextPool,
      )
    }

    val json = connection.getString("$urlPathPrefix/metadata/$lastCachedCommit")
    val outputs = getAllCompilationOutputs(sourceState = BuildTargetSourcesState.readJson(JsonReader(json.reader())), classOutDir = classOutDir)
    spanBuilder("download compilation output parts").setAttribute(AttributeKey.longKey("count"), outputs.size.toLong()).use {
      outputs.forEachConcurrent(downloadParallelism) { output ->
        downloadAndUnpackCompilationOutput(
          urlPathPrefix = urlPathPrefix,
          compilationOutput = output,
          notFound = notFound,
          totalDownloadedBytes = totalDownloadedBytes,
          connection = connection,
          zstdDecompressContextPool = zstdDecompressContextPool,
        )
      }
    }
    outputs.size
  }
}

private suspend fun downloadAndUnpackJpsCache(
  urlPathPrefix: String,
  commitHash: String,
  totalBytes: LongAdder,
  connection: Http2ClientConnection,
  cacheDestination: Path,
  zstdDecompressContextPool: ZstdDecompressContextPool,
) {
  val urlPath = "$urlPathPrefix/caches/$commitHash.zip.zstd"
  spanBuilder("download JPS Cache").setAttribute("urlPath", urlPath).setAttribute("outDir", cacheDestination.toString()).use { span ->
    val downloaded = connection.download(
      path = urlPath,
      file = cacheDestination,
      zstdDecompressContextPool = zstdDecompressContextPool,
      digestFactory = null,
      unzip = true,
    ).size
    if (downloaded == -1L) {
      Span.current().addEvent("resource not found")
      false
    }
    else {
      totalBytes.add(downloaded)
      true
    }
  }
}

private suspend fun downloadAndUnpackCompilationOutput(
  urlPathPrefix: String,
  compilationOutput: CompilationOutput,
  notFound: LongAdder,
  totalDownloadedBytes: LongAdder,
  connection: Http2ClientConnection,
  zstdDecompressContextPool: ZstdDecompressContextPool,
) {
  val urlPath = "$urlPathPrefix/${compilationOutput.remotePath}.zip.zstd"
  spanBuilder("download output").setAttribute("urlPath", urlPath).setAttribute("outDir", compilationOutput.path.toString()).use { span ->
    val downloaded = connection.download(
      path = urlPath,
      file = compilationOutput.path,
      zstdDecompressContextPool = zstdDecompressContextPool,
      unzip = true,
      ignoreNotFound = true,
    ).size
    if (downloaded == -1L) {
      // We assume that the error is logged by downloadToFile.
      // In the future, we will implement stricter behavior.
      // For now, the JPS cache reports non-existent compilation outputs, and we have to use such a workaround.
      span.addEvent("resource not found")
      notFound.increment()
    }
    else {
      totalDownloadedBytes.add(downloaded)
    }
  }
}