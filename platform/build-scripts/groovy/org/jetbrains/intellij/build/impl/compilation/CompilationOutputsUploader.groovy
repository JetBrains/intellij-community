// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.common.io.Files
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

import java.lang.reflect.Type
import java.util.concurrent.TimeUnit

@CompileStatic
class CompilationOutputsUploader {
  private final String commitHistoryFile = "commit_history.json"
  private final int commitsLimit = 200
  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String tmpDir
  private final Map<String, String> remotePerCommitHash
  private final SourcesStateProcessor sourcesStateProcessor

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, Map<String, String> remotePerCommitHash,
                             String agentPersistentStorage, String tmpDir) {
    this.agentPersistentStorage = agentPersistentStorage
    this.tmpDir = tmpDir
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.remotePerCommitHash = remotePerCommitHash
    this.context = context

    sourcesStateProcessor = new SourcesStateProcessor(context)
  }

  def upload() {
    JpsCompilationPartsUploader uploader = new JpsCompilationPartsUploader(remoteCacheUrl, context.messages)
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()

    try {
      def start = System.nanoTime()
      def dataStorageRoot = context.compilationData.dataStorageRoot
      def sourceStateFile = sourcesStateProcessor.sourceStateFile
      if (!sourceStateFile.exists()) {
        context.messages.
          warning("Compilation outputs doesn't contain source state file, please enable 'org.jetbrains.jps.portable.caches' flag")
        return
      }
      Map<String, Map<String, BuildTargetState>> currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()
      def commitHash = getCommitHash()

      executor.submit {
        // Upload jps caches started first because of the significant size of the output
        def sourcePath = "caches/$commitHash"
        if (uploader.isExist(sourcePath)) return
        File zipFile = new File(dataStorageRoot.parent, commitHash)
        zipBinaryData(zipFile, dataStorageRoot)
        uploader.upload(sourcePath, zipFile)
        File zipCopy = new File(tmpDir, sourcePath)
        FileUtil.copy(zipFile, zipCopy)
        FileUtil.delete(zipFile)

        // Upload compilation metadata
        sourcePath = "metadata/$commitHash"
        if (uploader.isExist(sourcePath)) return
        uploader.upload(sourcePath, sourceStateFile)
        File sourceStateFileCopy = new File(tmpDir, sourcePath)
        FileUtil.copy(sourceStateFile, sourceStateFileCopy)
        return
      }

      uploadCompilationOutputs(currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))

      // Publish metadata file
      def metadataFile = new File("$agentPersistentStorage/metadata.json")
      Files.copy(sourceStateFile, metadataFile)
      messages.artifactBuilt(metadataFile.absolutePath)
      FileUtil.delete(sourceStateFile)

      updateCommitHistory(uploader)
    }
    finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  void uploadCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState,
                                JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).forEach { CompilationOutput it ->
      uploadCompilationOutput(it, uploader, executor)
    }
  }

  private void uploadCompilationOutput(CompilationOutput compilationOutput, JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    executor.submit {
      def sourcePath = "${compilationOutput.type}/${compilationOutput.name}/${compilationOutput.hash}"
      def outputFolder = new File(compilationOutput.path)
      File zipFile = new File(outputFolder.getParent(), compilationOutput.hash)
      zipBinaryData(zipFile, outputFolder)
      if (!uploader.isExist(sourcePath)) {
        uploader.upload(sourcePath, zipFile)
      }
      File zipCopy = new File(tmpDir, sourcePath)
      FileUtil.copy(zipFile, zipCopy)
      FileUtil.delete(zipFile)
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
    if (uploader.isExist(commitHistoryFile)) {
      def content = uploader.getAsString(commitHistoryFile)
      if (!content.isEmpty()) {
        Type type = new TypeToken<Map<String, List<String>>>(){}.getType()
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
        if (listOfCommits.size() > commitsLimit) commitHistory.put(key, listOfCommits.takeRight(commitsLimit))
      }
    }

    // Upload and publish file with commits history
    def jsonAsString = new Gson().toJson(commitHistory)
    def file = new File("$agentPersistentStorage/$commitHistoryFile")
    file.write(jsonAsString)
    messages.artifactBuilt(file.absolutePath)
    uploader.upload(commitHistoryFile, file)
    File commitHistoryFileCopy = new File(tmpDir, commitHistoryFile)
    FileUtil.copy(file, commitHistoryFileCopy)
    FileUtil.delete(file)
  }

  private String getCommitHash() {
    if (remotePerCommitHash.size() == 1) return remotePerCommitHash.values().first()
    StringBuilder commitHashBuilder = new StringBuilder()
    int hashLength = (remotePerCommitHash.values().first().length() / remotePerCommitHash.size()) as int
    remotePerCommitHash.each { key, value ->
      commitHashBuilder.append(value.substring(0, hashLength))
    }
    return commitHashBuilder.toString()
  }

  @CompileStatic
  private static class JpsCompilationPartsUploader extends CompilationPartsUploader {
    private JpsCompilationPartsUploader(@NotNull String serverUrl, @NotNull BuildMessages messages) {
      super(serverUrl, messages)
    }

    boolean isExist(@NotNull final String path) {
      int code = doHead(path)
      if (code == 200) {
        log("File '$path' already exist on server, nothing to upload")
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
      return super.upload(path, file, false)
    }
  }
}
