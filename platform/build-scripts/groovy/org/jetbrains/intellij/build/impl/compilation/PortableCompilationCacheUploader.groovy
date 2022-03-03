// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.util.io.Compressor
import groovy.transform.CompileStatic
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.entity.ContentType
import org.apache.http.util.EntityUtils
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.cache.BuildTargetState
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.jps.incremental.storage.ProjectStamps

import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
final class PortableCompilationCacheUploader {
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String syncFolder
  private final boolean uploadCompilationOutputsOnly
  private final boolean forcedUpload

  private final AtomicInteger uploadedOutputsCount = new AtomicInteger()

  private final SourcesStateProcessor sourcesStateProcessor = new SourcesStateProcessor(context)
  private final Uploader uploader = new Uploader(remoteCacheUrl, context.messages)

  private final String remoteGitUrl
  private final String commitHash
  private final CommitsHistory commitsHistory = new CommitsHistory([(remoteGitUrl): [commitHash].toSet()])

  PortableCompilationCacheUploader(CompilationContext context, String remoteCacheUrl,
                                   String remoteGitUrl, String commitHash,
                                   String syncFolder, boolean uploadCompilationOutputsOnly,
                                   boolean forcedUpload) {
    this.syncFolder = syncFolder
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.remoteGitUrl = remoteGitUrl
    this.commitHash = commitHash
    this.context = context
    this.uploadCompilationOutputsOnly = uploadCompilationOutputsOnly
    this.forcedUpload = forcedUpload
  }

  def upload() {
    if (!sourcesStateProcessor.sourceStateFile.exists()) {
      context.messages.warning("Compilation outputs doesn't contain source state file, " +
                               "please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag")
      return
    }
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()
    try {
      def start = System.nanoTime()
      if (!uploadCompilationOutputsOnly) {
        executor.submit {
          // Jps Caches upload is started first because of significant size
          uploadJpsCaches()
        }
      }

      def currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()
      uploadCompilationOutputs(currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))
      def totalOutputs = String.valueOf(sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).size())
      messages.reportStatisticValue("Total outputs", totalOutputs)
      messages.reportStatisticValue("Uploaded outputs", String.valueOf(uploadedOutputsCount.get()))

      uploadMetadata()
    }
    finally {
      executor.close()
      CloseStreamUtil.closeStream(uploader)
    }
  }

  File buildJpsCacheZip() {
    File dataStorageRoot = context.compilationData.dataStorageRoot
    File zipFile = new File(dataStorageRoot.parent, commitHash)
    zipBinaryData(zipFile, dataStorageRoot)
    return zipFile
  }

  private void uploadJpsCaches() {
    File zipFile = buildJpsCacheZip()
    String cachePath = "caches/$commitHash"
    if (forcedUpload || !uploader.isExist(cachePath, true)) {
      uploader.upload(cachePath, zipFile)
    }
    File zipCopy = new File(syncFolder, cachePath)
    move(zipFile, zipCopy)
  }

  private void uploadMetadata() {
    String metadataPath = "metadata/$commitHash"
    File sourceStateFile = sourcesStateProcessor.sourceStateFile
    uploader.upload(metadataPath, sourceStateFile)
    File sourceStateFileCopy = new File(syncFolder, metadataPath)
    move(sourceStateFile, sourceStateFileCopy)
  }

  private void uploadCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState,
                                        Uploader uploader, NamedThreadPoolExecutor executor) {
    sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).forEach { CompilationOutput it ->
      uploadCompilationOutput(it, uploader, executor)
    }
  }

  private void uploadCompilationOutput(CompilationOutput compilationOutput,
                                       Uploader uploader,
                                       NamedThreadPoolExecutor executor) {
    executor.submit {
      def sourcePath = compilationOutput.remotePath
      def outputFolder = new File(compilationOutput.path)
      if (!outputFolder.exists()) {
        context.messages.warning("$outputFolder doesn't exist, was a respective module removed?")
        return
      }
      if (!outputFolder.isDirectory()) {
        context.messages.error("$outputFolder isn't a directory")
      }
      File zipFile = new File(outputFolder.getParent(), compilationOutput.hash)
      zipBinaryData(zipFile, outputFolder)
      if (!uploader.isExist(sourcePath)) {
        uploader.upload(sourcePath, zipFile)
        uploadedOutputsCount.incrementAndGet()
      }
      File zipCopy = new File(syncFolder, sourcePath)
      move(zipFile, zipCopy)
    }
  }

  private void zipBinaryData(File zipFile, File dir) {
    new Compressor.Zip(zipFile).withCloseable { zip ->
      try {
        zip.addDirectory(dir)
      }
      catch (IOException e) {
        context.messages.error("Couldn't compress binary data: $dir", e)
      }
    }
  }

  /**
   * Upload and publish file with commits history
   */
  void updateCommitHistory(CommitsHistory commitsHistory = this.commitsHistory,
                           boolean overrideRemoteHistory = false) {
    for (commitHash in commitsHistory.commitsForRemote(remoteGitUrl)) {
      def cacheUploaded = uploader.isExist("caches/$commitHash")
      def metadataUploaded = uploader.isExist("metadata/$commitHash")
      if (!cacheUploaded && !metadataUploaded) {
        def msg = "Unable to publish $commitHash due to missing caches/$commitHash and metadata/$commitHash. " +
                  "Probably caused by previous cleanup build."
        overrideRemoteHistory ? context.messages.error(msg) : context.messages.warning(msg)
        return
      }
      if (cacheUploaded != metadataUploaded) {
        context.messages.error("JPS Caches are uploaded: $cacheUploaded, metadata is uploaded: $metadataUploaded")
      }
    }
    if (!overrideRemoteHistory) commitsHistory += remoteCommitHistory()
    uploader.upload(CommitsHistory.JSON_FILE, writeCommitHistory(commitsHistory))
  }

  CommitsHistory remoteCommitHistory() {
    if (uploader.isExist(CommitsHistory.JSON_FILE)) {
      def json = uploader.getAsString(CommitsHistory.JSON_FILE)
      new CommitsHistory(json)
    }
    else {
      new CommitsHistory([:])
    }
  }

  private File writeCommitHistory(CommitsHistory commitsHistory) {
    File commitHistoryFile = new File(syncFolder, CommitsHistory.JSON_FILE)
    commitHistoryFile.parentFile.mkdirs()
    def json = commitsHistory.toJson()
    commitHistoryFile.write(json)
    context.messages.block(CommitsHistory.JSON_FILE) {
      context.messages.info(json)
    }
    return commitHistoryFile
  }

  private static move(File src, File dst) {
    if (!src.exists()) throw new IllegalStateException("File $src doesn't exist.")
    FileUtil.rename(src, dst)
    if (!dst.exists()) throw new IllegalStateException("File $dst doesn't exist.")
  }

  @CompileStatic
  private static class Uploader extends CompilationPartsUploader {
    private Uploader(@NotNull String serverUrl, @NotNull BuildMessages messages) {
      super(serverUrl, messages)
    }

    boolean isExist(@NotNull final String path, boolean logIfExists = false) {
      int code = doHead(path)
      if (code == 200) {
        if (logIfExists) {
          log("File '$path' already exists on server, nothing to upload")
        }
        return true
      }
      if (code != 404) {
        error("HEAD $path responded with unexpected $code")
      }
      return false
    }

    String getAsString(@NotNull final String path) throws UploadException {
      CloseableHttpResponse response = null
      try {
        String url = myServerUrl + StringUtil.trimStart(path, '/')
        debug("GET " + url)

        def request = new HttpGet(url)
        response = executeWithRetry(request)

        return EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_OCTET_STREAM.charset)
      }
      catch (Exception e) {
        throw new UploadException("Failed to GET $path: " + e.getMessage(), e)
      }
      finally {
        CloseStreamUtil.closeStream(response)
      }
    }

    boolean upload(@NotNull final String path, @NotNull final File file) {
      log("Uploading '$path'.")
      return super.upload(path, file, false)
    }
  }
}
