// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.cache.BuildTargetState
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor

import java.lang.reflect.Type

@CompileStatic
class CompilationOutputsDownloader {
  private static final Type COMMITS_HISTORY_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType()
  private static final int COMMITS_COUNT = 1_000
  private static final int COMMITS_SEARCH_TIMEOUT = 10_000

  private final GetClient getClient = new GetClient()

  private final CompilationContext context
  private final String remoteCacheUrl
  private final String gitUrl

  private final NamedThreadPoolExecutor executor

  private final SourcesStateProcessor sourcesStateProcessor

  CompilationOutputsDownloader(CompilationContext context, String remoteCacheUrl, String gitUrl) {
    this.context = context
    this.remoteCacheUrl = StringUtil.trimEnd(remoteCacheUrl, '/')
    this.gitUrl = gitUrl

    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("Using $executorThreadsCount threads to download caches.")
    executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)

    sourcesStateProcessor = new SourcesStateProcessor(context)
  }

  void downloadCachesAndOutput() {
    Set<String> availableCachesKeys = getAvailableCachesKeys()

    def commits = getLastCommits()
    int depth = commits.findIndexOf { availableCachesKeys.contains(it) }

    if (depth != -1) {
      String lastCachedCommit = commits[depth]
      context.messages.info("Using cache for commit $lastCachedCommit ($depth behind last commit).")

      executor.submit {
        saveCache(lastCachedCommit)
      }

      def sourcesState = getSourcesState(lastCachedCommit)
      def outputs = sourcesStateProcessor.getAllCompilationOutputs(sourcesState)
      context.messages.info("Going to download ${outputs.size()} compilation output parts.")
      outputs.forEach { CompilationOutput output ->
        executor.submit {
          saveOutput(output)
        }
      }
      executor.waitForAllComplete(context.messages)
    }
    else {
      context.messages.warning("Unable to find cache for any of last $COMMITS_COUNT commits.")
    }
  }

  private Map<String, Map<String, BuildTargetState>> getSourcesState(String commitHash) {
    return getClient.
      doGet("$remoteCacheUrl/metadata/$commitHash", SourcesStateProcessor.SOURCES_STATE_TYPE) as Map<String, Map<String, BuildTargetState>>
  }

  private void saveCache(String commitHash) {
    File cacheArchive = null
    try {
      cacheArchive = downloadCache(commitHash)

      def cacheDestination = context.compilationData.dataStorageRoot

      long start = System.currentTimeMillis()
      new Decompressor.Zip(cacheArchive).overwrite(true).extract(cacheDestination)
      context.messages.info("Cache was uncompresed to $cacheDestination in ${System.currentTimeMillis() - start}ms.")
    }
    finally {
      cacheArchive?.delete()
    }
  }

  private File downloadCache(String commitHash) {
    File cacheArchive = File.createTempFile('cache', '.zip')

    context.messages.info('Downloading cache...')
    long start = System.currentTimeMillis()
    InputStream cacheIS = getClient.doGet("$remoteCacheUrl/caches/$commitHash")
    try {
      cacheArchive << cacheIS
    }
    finally {
      cacheIS.close()
    }
    context.messages.info("Cache was downloaded in ${System.currentTimeMillis() - start}ms.")

    return cacheArchive
  }

  private void saveOutput(CompilationOutput compilationOutput) {
    File outputArchive = null
    try {
      outputArchive = downloadOutput(compilationOutput)

      new Decompressor.Zip(outputArchive).overwrite(true).extract(new File(compilationOutput.path))
    }
    finally {
      outputArchive?.delete()
    }
  }

  private File downloadOutput(CompilationOutput compilationOutput) {
    InputStream outputIS =
      getClient.doGet("$remoteCacheUrl/${compilationOutput.type}/${compilationOutput.name}/${compilationOutput.hash}")

    try {
      def outputArchive = new File(compilationOutput.path, 'tmp-output.zip')
      FileUtil.createParentDirs(outputArchive)
      outputArchive << outputIS
      return outputArchive
    }
    finally {
      outputIS.close()
    }
  }

  private Set<String> getAvailableCachesKeys() {
    def commitsHistory = getClient.doGet("$remoteCacheUrl/commit_history.json", COMMITS_HISTORY_TYPE)
    return commitsHistory[gitUrl] as Set<String>
  }

  private List<String> getLastCommits() {
    def proc = "git log -$COMMITS_COUNT --pretty=tformat:%H".execute((List)null, new File(context.paths.projectHome.trim()))
    def output = new StringBuffer()
    proc.consumeProcessOutputStream(output)
    proc.waitForOrKill(COMMITS_SEARCH_TIMEOUT)
    if (proc.exitValue() != 0) {
      throw new IllegalStateException("git log failed: ${proc.getErrorStream().getText()}")
    }

    return output.readLines()*.trim()
  }
}

@CompileStatic
class GetClient {
  private final CloseableHttpClient httpClient = HttpClientBuilder.create()
    .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
    .setMaxConnTotal(10)
    .setMaxConnPerRoute(10)
    .build()

  private final Gson gson = new Gson()

  def doGet(String url, Type responseType) {
    CloseableHttpResponse response = null
    def request = new HttpGet(url)
    try {
      response = httpClient.execute(request)

      def responseString = EntityUtils.toString(response.entity, ContentType.APPLICATION_JSON.charset)
      return gson.fromJson(responseString, responseType)
    }
    catch (Exception ex) {
      throw new DownloadException(url, ex)
    }
    finally {
      StreamUtil.closeStream(response)
    }
  }

  // It's a caller-side responsibility to close the returned stream
  InputStream doGet(String url) {
    try {
      def request = new HttpGet(url)
      CloseableHttpResponse response = httpClient.execute(request)

      return response.entity.content
    }
    catch (Exception ex) {
      throw new DownloadException(url, ex)
    }
  }

  @CompileStatic
  static class DownloadException extends RuntimeException {
    DownloadException(String url, Throwable cause) {
      super("Error while executing GET '$url': $cause.message")
    }
  }
}
