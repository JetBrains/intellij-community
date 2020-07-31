// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpHead
import org.apache.http.entity.ContentType
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClientBuilder
import org.apache.http.impl.client.LaxRedirectStrategy
import org.apache.http.util.EntityUtils
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.cache.BuildTargetState
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.intellij.build.impl.retry.Retry
import org.jetbrains.intellij.build.impl.retry.StopTrying

import java.lang.reflect.Type

@CompileStatic
class CompilationOutputsDownloader implements AutoCloseable {
  private static final Type COMMITS_HISTORY_TYPE = new TypeToken<Map<String, Set<String>>>() {}.getType()
  private static final int COMMITS_COUNT = 1_000

  private final GetClient getClient = new GetClient(context.messages)
  private final Git git = new Git(context.paths.projectHome.trim())

  private final CompilationContext context
  private final String remoteCacheUrl
  private final String gitUrl

  private final NamedThreadPoolExecutor executor

  private final SourcesStateProcessor sourcesStateProcessor

  private boolean availableForHeadCommitForced = false
  /**
   * If true then latest commit in current repository will be used to download caches.
   */
  @Lazy
  boolean availableForHeadCommit = { availableCommitDepth == 0 }()

  @Lazy
  private List<String> lastCommits = { git.log(COMMITS_COUNT) }()

  @Lazy
  private int availableCommitDepth = {
    availableForHeadCommitForced ? 0 : lastCommits.findIndexOf {
      availableCachesKeys.contains(it)
    }
  }()

  private String defaultBranch
  @Lazy
  private Set<String> availableCachesKeys = {
    CommitsHistory commitsHistory = new CommitsHistory(git.currentBranch(true), defaultBranch)

    def masterCommitsHistory = getClient.doGet("$remoteCacheUrl/${commitsHistory.defaultBranchPath}", COMMITS_HISTORY_TYPE)
    Set<String> branchCommits = Collections.emptySet()
    if (!commitsHistory.isDefaultBranch) {
      context.messages.info("Using ${commitsHistory.path} to get additional cache keys.")

      String branchCommitHistoryUrl = "$remoteCacheUrl/${commitsHistory.path}"
      if (getClient.exists(branchCommitHistoryUrl)) {
        def branchCommitsHistory = getClient.doGet(branchCommitHistoryUrl, COMMITS_HISTORY_TYPE)
        branchCommits = branchCommitsHistory[gitUrl] as Set<String>
      }
    }

    return (masterCommitsHistory[gitUrl] as Set<String>) + branchCommits
  }()

  CompilationOutputsDownloader(CompilationContext context, String remoteCacheUrl, String gitUrl,
                               boolean availableForHeadCommit, String defaultBranch) {
    this.context = context
    this.remoteCacheUrl = StringUtil.trimEnd(remoteCacheUrl, '/')
    this.gitUrl = gitUrl
    this.availableForHeadCommitForced = availableForHeadCommit
    this.defaultBranch = defaultBranch

    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)

    sourcesStateProcessor = new SourcesStateProcessor(context)
  }

  @Override
  void close() {
    executor.close()
    executor.reportErrors(context.messages)
  }

  void downloadCachesAndOutput() {
    if (availableCommitDepth != -1) {
      String lastCachedCommit = lastCommits[availableCommitDepth]
      if (lastCachedCommit == null) {
        context.messages.error("Unable to find last cached commit for $availableCommitDepth in $lastCommits")
      }
      context.messages.info("Using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit).")
      context.messages.info("Using $executor.corePoolSize threads to download caches.")
      // In case if outputs are available for the current commit
      // cache is not needed as we are not going to compile anything.
      if (!availableForHeadCommit) {
        executor.submit {
          saveCache(lastCachedCommit)
        }
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
    getClient.doGet("$remoteCacheUrl/caches/$commitHash", cacheArchive)
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
    def outputArchive = new File(compilationOutput.path, 'tmp-output.zip')
    FileUtil.createParentDirs(outputArchive)

    getClient.doGet("$remoteCacheUrl/${compilationOutput.type}/${compilationOutput.name}/${compilationOutput.hash}", outputArchive)

    return outputArchive
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

  private final BuildMessages buildMessages

  GetClient(BuildMessages buildMessages) {
    this.buildMessages = buildMessages
  }

  boolean exists(String url) {
    HttpHead request = new HttpHead(url)

    httpClient.execute(request).withCloseable { response ->
      response.statusLine.statusCode == 200
    }
  }

  def doGet(String url, Type responseType) {
    CloseableHttpResponse response = null
    return getWithRetry(url, { HttpGet request ->
      response = httpClient.execute(request)
      def responseString = EntityUtils.toString(response.entity, ContentType.APPLICATION_JSON.charset)
      if (response.statusLine.statusCode != HttpStatus.SC_OK) {
        DownloadException downloadException = new DownloadException(url, response.statusLine.statusCode, responseString)
        throwDownloadException(response, downloadException)
      }
      return gson.fromJson(responseString, responseType)
    }, { StreamUtil.closeStream(response) })
  }

  void doGet(String url, File file) {
    CloseableHttpResponse response = null
    getWithRetry(url, { HttpGet request ->
      response = httpClient.execute(request)
      if (response.statusLine.statusCode != HttpStatus.SC_OK) {
        DownloadException downloadException = new DownloadException(url, response.statusLine.statusCode, response.entity.content.text)
        throwDownloadException(response, downloadException)
      }
      file << response.entity.content
      return
    }, { StreamUtil.closeStream(response) })
  }

  private static void throwDownloadException(CloseableHttpResponse response, DownloadException downloadException) {
    if (response.statusLine.statusCode == HttpStatus.SC_NOT_FOUND) {
      throw new StopTrying(downloadException)
    }
    else {
      throw downloadException
    }
  }

  private <T> T getWithRetry(String url, Closure<T> operation, Closure<T> finalizer = {}) {
    return new Retry(buildMessages).call {
      try {
        operation(new HttpGet(url))
      }
      catch (StopTrying | DownloadException ex) {
        throw ex
      }
      catch (Exception ex) {
        throw new DownloadException(url, ex)
      }
      finally {
        finalizer()
      }
    }
  }

  @CompileStatic
  static class DownloadException extends RuntimeException {
    DownloadException(String url, int status, String details) {
      super("Error while executing GET '$url': $status, $details")
    }

    DownloadException(String url, Throwable cause) {
      super("Error while executing GET '$url': $cause.message")
    }
  }
}
