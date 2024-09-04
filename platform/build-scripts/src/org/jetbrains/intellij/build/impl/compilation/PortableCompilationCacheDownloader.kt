// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.stream.JsonReader
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.*
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.getAllCompilationOutputs
import org.jetbrains.intellij.build.retryWithExponentialBackOff
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.incremental.storage.BuildTargetSourcesState
import java.net.URI
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

private const val COMMITS_COUNT = 1_000

internal class PortableCompilationCacheDownloader(private val context: CompilationContext, private val git: Git) {
  private val lastCommits by lazy { git.log(COMMITS_COUNT) }

  private suspend fun downloadToFile(urlPath: String, file: Path, spanName: String, notFound: LongAdder, connection: Http2ClientConnection?): Long {
    return spanBuilder(spanName).setAttribute("urlPath", urlPath).setAttribute("path", file.toString()).use { span ->
      if (connection == null) {
        Files.createDirectories(file.parent)
        require(urlPath.isS3())
        retryWithExponentialBackOff {
          awsS3Cli("cp", urlPath, file.toString())
        }
        return@use try {
          Files.size(file)
        }
        catch (e: NoSuchFileException) {
          0
        }
      }

      val sizeOnDisk = connection.download(path = urlPath, file = file)
      if (sizeOnDisk == -1L) {
        span.addEvent("resource not found")
        notFound.increment()
      }
      sizeOnDisk
    }
  }

  private suspend fun getAvailableCachesKeysLazyTask(urlPathPrefix: String, gitUrl: String, connection: Http2ClientConnection?): Collection<String> {
    val commitHistoryUrl = "$urlPathPrefix/${CommitsHistory.JSON_FILE}"
    require(!commitHistoryUrl.isS3() && connection != null)
    val json: Map<String, Set<String>> = connection.getJsonOrDefaultIfNotFound(path = commitHistoryUrl, defaultIfNotFound = emptyMap())
    if (json.isEmpty()) {
      return emptyList()
    }
    return CommitsHistory(json).commitsForRemote(gitUrl)
  }

  private suspend fun prepareDownload(urlPathPrefix: String, gitUrl: String, connection: Http2ClientConnection?): Pair<String, Int>? {
    val availableCachesKeys = getAvailableCachesKeysLazyTask(urlPathPrefix = urlPathPrefix, gitUrl = gitUrl, connection = connection)
    val availableCommitDepth = lastCommits.indexOfFirst {
      availableCachesKeys.contains(it)
    }

    if (availableCommitDepth !in 0 until lastCommits.count()) {
      Span.current().addEvent("unable to find cache for any of last ${lastCommits.count()} commits.")
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

  suspend fun download(cacheUrl: String, authHeader: CharSequence, gitUrl: String): Int {
    val start = System.nanoTime()
    val totalDownloadedBytes = LongAdder()
    val notFound = LongAdder()
    var availableCommitDepth = -1
    val total = if (cacheUrl.isS3()) {
      val info = prepareDownload(urlPathPrefix = cacheUrl, gitUrl = gitUrl, connection = null) ?: return -1
      availableCommitDepth = info.second
      doDownload(
        urlPathPrefix = cacheUrl,
        lastCachedCommit = info.first,
        notFound = notFound,
        totalDownloadedBytes = totalDownloadedBytes,
        connection = null,
      )
    }
    else {
      val serverUri = URI(cacheUrl)
      withHttp2ClientConnectionFactory(trustAll = serverUri.host == "127.0.0.1") { client ->
        client.connect(serverUri.host, serverUri.port).withAuth(authHeader).use { connection ->
          val info = prepareDownload(urlPathPrefix = serverUri.path, gitUrl = gitUrl, connection = connection) ?: return@use -1
          availableCommitDepth = info.second
          doDownload(
            urlPathPrefix = serverUri.path,
            lastCachedCommit = info.first,
            notFound = notFound,
            totalDownloadedBytes = totalDownloadedBytes,
            connection = connection,
          )
        }
      }
    }

    if (availableCommitDepth == -1) {
      return -1
    }

    reportStatisticValue("jps-cache:download:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())
    reportStatisticValue("jps-cache:downloaded:bytes", totalDownloadedBytes.sum().toString())
    reportStatisticValue("jps-cache:downloaded:count", total.toString())
    reportStatisticValue("jps-cache:notFound:count", notFound.sum().toString())

    return availableCommitDepth
  }

  private suspend fun PortableCompilationCacheDownloader.doDownload(
    urlPathPrefix: String,
    lastCachedCommit: String,
    notFound: LongAdder,
    totalDownloadedBytes: LongAdder,
    connection: Http2ClientConnection?,
  ): Int {
    return coroutineScope {
      launch {
        spanBuilder("get and unpack jps cache").setAttribute("commit", lastCachedCommit).use {
          downloadAndUnpackJpsCache(
            urlPathPrefix = urlPathPrefix,
            commitHash = lastCachedCommit,
            notFound = notFound,
            totalBytes = totalDownloadedBytes,
            connection = connection,
          )
        }
      }

      val metadataUrlPath = "$urlPathPrefix/metadata/$lastCachedCommit"
      val json = if (connection == null) {
        require(metadataUrlPath.isS3())
        retryWithExponentialBackOff {
          awsS3Cli("cp", metadataUrlPath, "-")
        }
      }
      else {
        connection.getString(path = metadataUrlPath)
      }

      val outputs = getAllCompilationOutputs(sourceState = BuildTargetSourcesState.readJson(JsonReader(json.reader())), classOutDir = context.classesOutputDirectory)
      spanBuilder("download compilation output parts").setAttribute(AttributeKey.longKey("count"), outputs.size.toLong()).use {
        outputs.forEachConcurrent(downloadParallelism) { output ->
          spanBuilder("get and unpack output").setAttribute("part", output.remotePath).use {
            downloadAndUnpackCompilationOutput(
              urlPathPrefix = urlPathPrefix,
              compilationOutput = output,
              notFound = notFound,
              totalDownloadedBytes = totalDownloadedBytes,
              connection = connection,
            )
          }
        }
      }
      outputs.size
    }
  }

  private suspend fun downloadAndUnpackJpsCache(urlPathPrefix: String, commitHash: String, notFound: LongAdder, totalBytes: LongAdder, connection: Http2ClientConnection?) {
    val cacheArchive = Files.createTempFile("cache", ".zip")
    try {
      val sizeOnDisk = downloadToFile(
        urlPath = "$urlPathPrefix/caches/$commitHash",
        file = cacheArchive,
        spanName = "download jps cache",
        notFound = notFound,
        connection = connection,
      )

      require(sizeOnDisk > 0)
      totalBytes.add(sizeOnDisk)
      val cacheDestination = context.compilationData.dataStorageRoot

      spanBuilder("unpack jps cache")
        .setAttribute("archive", cacheArchive.toString())
        .setAttribute("destination", cacheDestination.toString())
        .use(Dispatchers.IO) {
          unpackArchiveUsingNettyByteBufferPool(archiveFile = cacheArchive, outDir = cacheDestination, isCompressed = true)
        }
    }
    finally {
      Files.deleteIfExists(cacheArchive)
    }
  }

  private suspend fun downloadAndUnpackCompilationOutput(
    urlPathPrefix: String,
    compilationOutput: CompilationOutput,
    notFound: LongAdder,
    totalDownloadedBytes: LongAdder,
    connection: Http2ClientConnection?,
  ) {
    val tempFile = compilationOutput.path.resolve("tmp-output.zip")
    val urlPath = "$urlPathPrefix/${compilationOutput.remotePath.trimStart('/')}"
    val sizeOnDisk = downloadToFile(
      urlPath = urlPath,
      file = tempFile,
      spanName = "download output",
      notFound = notFound,
      connection = connection,
    )

    if (sizeOnDisk <= 0) {
      return
    }

    // We assume that the error is logged by downloadToFile.
    // In the future, we will implement stricter behavior.
    // For now, the JPS cache reports non-existent compilation outputs, and we have to use such a workaround.
    totalDownloadedBytes.add(sizeOnDisk)

    try {
      spanBuilder("unpack output")
        .setAttribute("archive", tempFile.toString())
        .setAttribute("destination", compilationOutput.path.toString())
        .use(Dispatchers.IO) {
          unpackArchiveUsingNettyByteBufferPool(archiveFile = tempFile, outDir = compilationOutput.path, isCompressed = true)
        }
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      throw Exception("Unable to unpack $urlPath to ${compilationOutput.path}", e)
    }
    finally {
      Files.deleteIfExists(tempFile)
    }
  }

  private fun reportStatisticValue(key: String, value: String) {
    context.messages.reportStatisticValue(key, value)
  }
}