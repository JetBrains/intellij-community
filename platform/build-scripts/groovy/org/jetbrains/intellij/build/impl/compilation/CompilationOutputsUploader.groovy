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
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor
import org.jetbrains.jps.incremental.storage.ProjectStamps

import java.lang.reflect.Type
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@CompileStatic
class CompilationOutputsUploader {
  private static final int COMMITS_LIMIT = 200

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
  private String commitsHistoryPath = {
    Git git = new Git(context.paths.projectHome.trim())
    return new CommitsHistory(git.currentBranch(false)).path
  }()

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

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, Map<String, String> remotePerCommitHash, String tmpDir,
                             boolean updateCommitHistory) {
    this.tmpDir = tmpDir
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.remotePerCommitHash = remotePerCommitHash
    this.context = context
    this.updateCommitHistory = updateCommitHistory
  }

  def upload(Boolean publishTeamCityArtifacts) {
    if (!sourcesStateProcessor.sourceStateFile.exists()) {
      context.messages.warning("Compilation outputs doesn't contain source state file, please enable '${ProjectStamps.PORTABLE_CACHES_PROPERTY}' flag")
      return
    }
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()
    try {
      def start = System.nanoTime()
      Map<String, Map<String, BuildTargetState>> currentSourcesState = sourcesStateProcessor.parseSourcesStateFile()
      // In case if commits history is not updated it makes no sense to upload
      // JPS caches archive as we're going to use hot compile outputs only and
      // not to perform any further compilations.
      if (updateCommitHistory) {
        executor.submit {
          // Upload jps caches started first because of the significant size of the output
          uploadCompilationCache(publishTeamCityArtifacts)
        }
      }

      uploadCompilationOutputs(currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))
      messages.reportStatisticValue("Total outputs", String.valueOf(sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).size()))
      messages.reportStatisticValue("Uploaded outputs", String.valueOf(uploadedOutputsCount.get()))

      uploadMetadata()
      if (updateCommitHistory) {
        updateCommitHistory(uploader)
      }
    }
    finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  private void uploadCompilationCache(Boolean publishTeamCityArtifacts) {
    String cachePath = "caches/$commitHash"
    def exists = uploader.isExist(cachePath)

    File dataStorageRoot = context.compilationData.dataStorageRoot
    File zipFile = new File(dataStorageRoot.parent, commitHash)
    zipBinaryData(zipFile, dataStorageRoot)
    if (!exists) {
      uploader.upload(cachePath, zipFile)
    }

    File zipCopy = new File(tmpDir, cachePath)
    move(zipFile, zipCopy)
    // Publish artifact for dependent configuration
    if (publishTeamCityArtifacts) context.messages.artifactBuilt(zipCopy.absolutePath)
  }

  private void uploadMetadata() {
    String metadataPath = "metadata/$commitHash"
    File sourceStateFile = sourcesStateProcessor.sourceStateFile
    uploader.upload(metadataPath, sourceStateFile)
    File sourceStateFileCopy = new File(tmpDir, metadataPath)
    move(sourceStateFile, sourceStateFileCopy)
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
      }
      File zipCopy = new File(tmpDir, sourcePath)
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

  private void updateCommitHistory(JpsCompilationPartsUploader uploader) {
    Map<String, List<String>> commitHistory = new HashMap<>()
    if (uploader.isExist(commitsHistoryPath, false)) {
      def content = uploader.getAsString(commitsHistoryPath)
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
    File commitHistoryFile = new File(tmpDir, commitsHistoryPath)
    commitHistoryFile.parentFile.mkdirs()
    commitHistoryFile.write(jsonAsString)

    uploader.upload(commitsHistoryPath, commitHistoryFile)
  }

  private static move(File src, File dst) {
    if (!src.exists()) throw new IllegalStateException("File $src doesn't exist.")
    FileUtil.rename(src, dst)
    if (!dst.exists()) throw new IllegalStateException("File $dst doesn't exist.")
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
        response = executeWithRetry(request)

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
