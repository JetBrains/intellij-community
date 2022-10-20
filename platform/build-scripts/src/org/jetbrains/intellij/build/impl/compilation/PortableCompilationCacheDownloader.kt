// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.util.io.Decompressor
import org.jetbrains.intellij.build.*
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.io.retryWithExponentialBackOff
import org.jetbrains.jps.cache.model.BuildTargetState
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ForkJoinTask

private const val COMMITS_COUNT = 1_000

internal class PortableCompilationCacheDownloader(
  private val  context: CompilationContext,
  private val git: Git,
  remoteCacheUrl: String,
  private val gitUrl: String,
  private val availableForHeadCommitForced: Boolean,
  private val downloadCompilationOutputsOnly: Boolean,
) {
  private val remoteCacheUrl = remoteCacheUrl.trimEnd('/')

  private val sourcesStateProcessor = SourcesStateProcessor(context.compilationData.dataStorageRoot, context.classesOutputDirectory)

  /**
   * If true then latest commit in current repository will be used to download caches.
   */
  val availableForHeadCommit by lazy { availableCommitDepth == 0 }

  private val lastCommits by lazy { git.log(COMMITS_COUNT) }

  private fun downloadString(url: String): String = retryWithExponentialBackOff {
    if (url.isS3()) {
      awsS3Cli("cp", url, "-")
    }
    else {
      httpClient.get(url).useSuccessful { it.body.string() }
    }
  }

  private fun downloadToFile(url: String, file: Path, spanName: String) {
    TraceManager.spanBuilder(spanName).setAttribute("url", url).setAttribute("path", "$file").useWithScope {
      Files.createDirectories(file.parent)
      retryWithExponentialBackOff {
        if (url.isS3()) {
          awsS3Cli("cp", url, "$file")
        } else {
          httpClient.get(url).useSuccessful { response ->
            Files.newOutputStream(file).use {
              response.body.byteStream().transferTo(it)
            }
          }
        }
      }
    }
  }

  private val availableCommitDepth by lazy {
    if (availableForHeadCommitForced) 0 else lastCommits.indexOfFirst {
      availableCachesKeys.contains(it)
    }
  }

  private val availableCachesKeys by lazy {
    val json = downloadString("${this.remoteCacheUrl}/${CommitsHistory.JSON_FILE}")
    CommitsHistory(json).commitsForRemote(gitUrl)
  }

  fun download() {
    if (availableCommitDepth in 0 until lastCommits.count()) {
      val lastCachedCommit = lastCommits.get(availableCommitDepth)
      context.messages.info("Using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit).")
      val tasks = mutableListOf<ForkJoinTask<*>>()
      if (!downloadCompilationOutputsOnly) {
        tasks.add(ForkJoinTask.adapt { saveJpsCache(lastCachedCommit) })
      }

      val sourcesState = getSourcesState(lastCachedCommit)
      val outputs = sourcesStateProcessor.getAllCompilationOutputs(sourcesState)
      context.messages.info("Going to download ${outputs.size} compilation output parts.")
      outputs.forEach { output ->
        tasks.add(ForkJoinTask.adapt { saveOutput(output) })
      }
      ForkJoinTask.invokeAll(tasks)
    }
    else {
      context.messages.warning("Unable to find cache for any of last $COMMITS_COUNT commits.")
    }
  }

  private fun getSourcesState(commitHash: String): Map<String, Map<String, BuildTargetState>> {
    return sourcesStateProcessor.parseSourcesStateFile(downloadString("$remoteCacheUrl/metadata/$commitHash"))
  }

  private fun saveJpsCache(commitHash: String) {
    var cacheArchive: Path? = null
    try {
      cacheArchive = downloadJpsCache(commitHash)

      val cacheDestination = context.compilationData.dataStorageRoot

      val start = System.currentTimeMillis()
      Decompressor.Zip(cacheArchive).overwrite(true).extract(cacheDestination)
      context.messages.info("Jps Cache was uncompressed to $cacheDestination in ${System.currentTimeMillis() - start}ms.")
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

  private fun saveOutput(compilationOutput: CompilationOutput) {
    var outputArchive: Path? = null
    try {
      outputArchive = downloadOutput(compilationOutput)
      Decompressor.Zip(outputArchive).overwrite(true).extract(Path.of(compilationOutput.path))
    }
    catch (e: Exception) {
      throw Exception("Unable to decompress $remoteCacheUrl/${compilationOutput.remotePath} to $compilationOutput.path", e)
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
}