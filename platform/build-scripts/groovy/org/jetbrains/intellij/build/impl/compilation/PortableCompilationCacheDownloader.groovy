// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation


import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Decompressor
import groovy.transform.CompileStatic
import org.apache.http.HttpStatus
import org.apache.http.client.config.RequestConfig
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

import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

@CompileStatic
class PortableCompilationCacheDownloader implements AutoCloseable {
  private static final int COMMITS_COUNT = 1_000

  private final GetClient getClient = new GetClient(context.messages)
  private final Git git = new Git(context.paths.projectHome.trim())

  private final CompilationContext context
  private final String remoteCacheUrl
  private final String gitUrl

  private final NamedThreadPoolExecutor executor

  private final SourcesStateProcessor sourcesStateProcessor = new SourcesStateProcessor(context)

  private boolean availableForHeadCommitForced
  private boolean downloadCompilationOutputsOnly
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

  @Lazy
  private Collection<String> availableCachesKeys = {
    def json = getClient.doGet("$remoteCacheUrl/$CommitsHistory.JSON_FILE")
    new CommitsHistory(json).commitsForRemote(gitUrl)
  }()

  PortableCompilationCacheDownloader(CompilationContext context, String remoteCacheUrl, String gitUrl,
                                     boolean availableForHeadCommit, boolean downloadCompilationOutputsOnly) {
    this.context = context
    this.remoteCacheUrl = StringUtil.trimEnd(remoteCacheUrl, '/')
    this.gitUrl = gitUrl
    this.availableForHeadCommitForced = availableForHeadCommit
    this.downloadCompilationOutputsOnly = downloadCompilationOutputsOnly

    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
  }

  @Lazy
  boolean anyLocalChanges = {
    def localChanges = git.status()
    if (!localChanges.isEmpty()) {
      context.messages.info('Local changes:')
      localChanges.each { context.messages.info("\t$it") }
    }
    !localChanges.isEmpty()
  }()

  @Override
  void close() {
    executor.close()
    executor.reportErrors(context.messages)
  }

  void download() {
    if (availableCommitDepth != -1) {
      String lastCachedCommit = lastCommits[availableCommitDepth]
      if (lastCachedCommit == null) {
        context.messages.error("Unable to find last cached commit for $availableCommitDepth in $lastCommits")
      }
      context.messages.info("Using cache for commit $lastCachedCommit ($availableCommitDepth behind last commit).")
      context.messages.info("Using $executor.corePoolSize threads to download caches.")
      if (!downloadCompilationOutputsOnly || anyLocalChanges) {
        executor.submit {
          saveJpsCache(lastCachedCommit)
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
    sourcesStateProcessor.parseSourcesStateFile(getClient.doGet("$remoteCacheUrl/metadata/$commitHash"))
  }

  private void saveJpsCache(String commitHash) {
    File cacheArchive = null
    try {
      cacheArchive = downloadJpsCache(commitHash)

      def cacheDestination = context.compilationData.dataStorageRoot

      long start = System.currentTimeMillis()
      new Decompressor.Zip(cacheArchive).overwrite(true).extract(cacheDestination)
      context.messages.info("Jps Cache was uncompresed to $cacheDestination in ${System.currentTimeMillis() - start}ms.")
    }
    finally {
      cacheArchive?.delete()
    }
  }

  private File downloadJpsCache(String commitHash) {
    File cacheArchive = File.createTempFile('cache', '.zip')

    context.messages.info('Downloading Jps Cache...')
    long start = System.currentTimeMillis()
    getClient.doGet("$remoteCacheUrl/caches/$commitHash", cacheArchive)
    context.messages.info("Jps Cache was downloaded in ${System.currentTimeMillis() - start}ms.")

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

    getClient.doGet("$remoteCacheUrl/${compilationOutput.remotePath}", outputArchive)

    return outputArchive
  }
}

@CompileStatic
class GetClient {
  private int timeout = TimeUnit.MINUTES.toMillis(1).toInteger()

  private final RequestConfig config = RequestConfig.custom()
    .setConnectionRequestTimeout(timeout)
    .setConnectTimeout(timeout)
    .setSocketTimeout(timeout)
    .build()

  private final CloseableHttpClient httpClient = HttpClientBuilder.create()
    .setRedirectStrategy(LaxRedirectStrategy.INSTANCE)
    .setDefaultRequestConfig(config)
    .setMaxConnTotal(10)
    .setMaxConnPerRoute(10)
    .build()

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

  String doGet(String url) {
    getWithRetry(url, { HttpGet request ->
      httpClient.execute(request).withCloseable { response ->
        def responseString = EntityUtils.toString(response.entity, ContentType.APPLICATION_JSON.charset)
        if (response.statusLine.statusCode != HttpStatus.SC_OK) {
          DownloadException downloadException = new DownloadException(url, response.statusLine.statusCode, responseString)
          throwDownloadException(response, downloadException)
        }
        responseString
      }
    })
  }

  void doGet(String url, File file) {
    getWithRetry(url, { HttpGet request ->
      httpClient.execute(request).withCloseable { response ->
        if (response.statusLine.statusCode != HttpStatus.SC_OK) {
          DownloadException downloadException = new DownloadException(url, response.statusLine.statusCode, response.entity.content.text)
          throwDownloadException(response, downloadException)
        }
        response.entity.content.withCloseable {
          Files.copy(it, file.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
      }
    })
  }

  private static void throwDownloadException(CloseableHttpResponse response, DownloadException downloadException) {
    if (response.statusLine.statusCode == HttpStatus.SC_NOT_FOUND) {
      throw new StopTrying(downloadException)
    }
    else {
      throw downloadException
    }
  }

  private <T> T getWithRetry(String url, Closure<T> operation) {
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
