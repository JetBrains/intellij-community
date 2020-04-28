// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
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
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.jps.incremental.storage.ProjectStamps

import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
class CompilationOutputsUploader {
  private static final String COMMIT_HISTORY_FILE = "commit_history.json"
  private static final int COMMITS_LIMIT = 200

  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String tmpDir
  private final Map<String, String> remotePerCommitHash
  private final boolean updateCommitHistory

  private final AtomicInteger uploadedOutputsCount = new AtomicInteger()

  private final SourcesStateProcessor sourcesStateProcessor = new SourcesStateProcessor(context)
  private final JpsCompilationPartsUploader uploader = new JpsCompilationPartsUploader(remoteCacheUrl, context.messages)

  @Lazy
  private String commitHash = {
    if (remotePerCommitHash.size() == 1) return remotePerCommitHash.values().first()
    StringBuilder commitHashBuilder = new StringBuilder()
    int hashLength = (remotePerCommitHash.values().first().length() / remotePerCommitHash.size()) as int
    remotePerCommitHash.each { key, value ->
      commitHashBuilder.append(value.substring(0, hashLength))
    }
    return commitHashBuilder.toString()
  }()

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, Map<String, String> remotePerCommitHash,
                             String agentPersistentStorage, String tmpDir, boolean updateCommitHistory) {
    this.agentPersistentStorage = agentPersistentStorage
    this.tmpDir = tmpDir
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.remotePerCommitHash = remotePerCommitHash
    this.context = context
    this.updateCommitHistory = updateCommitHistory
  }

  def upload(Boolean publishCaches) {
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()

    try {
      def start = System.nanoTime()
      def sourceStateFile = sourcesStateProcessor.sourceStateFile
      if (!sourceStateFile.exists()) {
        context.messages.
          warning("Compilation outputs doesn't contain source state file, please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag")
        return
      }
      Map<String, Map<String, BuildTargetState>> currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()

      executor.submit {
        // In case if commits history is not updated it makes no sense to upload
        // JPS caches archive as we're going to use hot compile outputs only and
        // not to perform any further compilations.
        if (updateCommitHistory) {
        // Upload jps caches started first because of the significant size of the output
          if (!uploadCompilationCache(publishCaches)) return
        }

        uploadMetadata()
      }

      uploadCompilationOutputs(currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))
      messages.reportStatisticValue("Total outputs", String.valueOf(sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).size()))
      messages.reportStatisticValue("Uploaded outputs", String.valueOf(uploadedOutputsCount.get()))

      // Publish metadata file
      def metadataFile = new File("$agentPersistentStorage/metadata.json")
      FileUtil.rename(sourceStateFile, metadataFile)
      messages.artifactBuilt(metadataFile.absolutePath)

      if (updateCommitHistory) {
        updateCommitHistory(uploader)
      }
    }
    finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  private boolean uploadCompilationCache(Boolean publishCaches) {
    String cachePath = "caches/$commitHash"
    if (uploader.isExist(cachePath)) return false

    File dataStorageRoot = context.compilationData.dataStorageRoot
    File zipFile = new File(dataStorageRoot.parent, commitHash)
    zipBinaryData(zipFile, dataStorageRoot)
    uploader.upload(cachePath, zipFile)

    // Publish artifact for dependent configuration
    if (publishCaches) {
      File zipArtifact = new File(tmpDir, "caches.zip")
      FileUtil.copy(zipFile, zipArtifact)
      context.messages.artifactBuilt(zipArtifact.absolutePath)
    }

    File zipCopy = new File(tmpDir, cachePath)
    FileUtil.rename(zipFile, zipCopy)
    return true
  }

  private void uploadMetadata() {
    String metadataPath = "metadata/$commitHash"
    if (uploader.isExist(metadataPath)) return

    File sourceStateFile = sourcesStateProcessor.sourceStateFile
    uploader.upload(metadataPath, sourceStateFile)
    File sourceStateFileCopy = new File(tmpDir, metadataPath)
    FileUtil.copy(sourceStateFile, sourceStateFileCopy)
  }

  void uploadCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState,
                                JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).forEach { CompilationOutput it ->
      uploadCompilationOutput(it, uploader, executor)
    }
  }

  private void uploadCompilationOutput(CompilationOutput compilationOutput,
                                       JpsCompilationPartsUploader uploader,
                                       NamedThreadPoolExecutor executor) {
    executor.submit {
      def sourcePath = "${compilationOutput.type}/${compilationOutput.name}/${compilationOutput.hash}"
      def outputFolder = new File(compilationOutput.path)
      File zipFile = new File(outputFolder.getParent(), compilationOutput.hash)
      zipBinaryData(zipFile, outputFolder)
      if (!uploader.isExist(sourcePath, false)) {
        uploader.upload(sourcePath, zipFile)
        uploadedOutputsCount.incrementAndGet()
        File zipCopy = new File(tmpDir, sourcePath)
        FileUtil.rename(zipFile, zipCopy)
      }
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

  private void updateCommitHistory(JpsCompilationPartsUploader uploader) {
    Map<String, List<String>> commitHistory = new HashMap<>()
    if (uploader.isExist(COMMIT_HISTORY_FILE, false)) {
      def content = uploader.getAsString(COMMIT_HISTORY_FILE)
      if (!content.isEmpty()) {
        Type type = new TypeToken<Map<String, List<String>>>() {}.getType()
        commitHistory = new Gson().fromJson(content, type) as Map<String, List<String>>
      }
    }

    remotePerCommitHash.each { key, value ->
      def listOfCommits = commitHistory.get(key)
      if (listOfCommits == null) {
        def newList = new ArrayList()
        newList.add(value)
        commitHistory.put(key, newList)
      }
      else {
        listOfCommits.add(value)
        if (listOfCommits.size() > COMMITS_LIMIT) commitHistory.put(key, listOfCommits.takeRight(COMMITS_LIMIT))
      }
    }

    // Upload and publish file with commits history
    def jsonAsString = new Gson().toJson(commitHistory)
    def file = new File("$agentPersistentStorage/$COMMIT_HISTORY_FILE")
    file.write(jsonAsString)
    messages.artifactBuilt(file.absolutePath)
    uploader.upload(COMMIT_HISTORY_FILE, file)
    File commitHistoryFileCopy = new File(tmpDir, COMMIT_HISTORY_FILE)
    FileUtil.rename(file, commitHistoryFileCopy)
  }

  @CompileStatic
  private static class JpsCompilationPartsUploader extends CompilationPartsUploader {
    private JpsCompilationPartsUploader(@NotNull String serverUrl, @NotNull BuildMessages messages) {
      super(serverUrl, messages)
    }

    boolean isExist(@NotNull final String path, boolean logIfExists = true) {
      int code = doHead(path)
      if (code == 200) {
        if (logIfExists) {
          log("File '$path' already exist on server, nothing to upload")
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
        response = myHttpClient.execute(request)

        return EntityUtils.toString(response.getEntity(), ContentType.APPLICATION_OCTET_STREAM.charset)
      }
      catch (Exception e) {
        throw new UploadException("Failed to GET $path: " + e.getMessage(), e)
      }
      finally {
        StreamUtil.closeStream(response)
      }
    }

    boolean upload(@NotNull final String path, @NotNull final File file) {
      log("Uploading '$path'.")
      return super.upload(path, file, false)
    }
  }
}
