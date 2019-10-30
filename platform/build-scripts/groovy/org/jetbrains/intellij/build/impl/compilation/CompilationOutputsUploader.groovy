// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.common.io.Files
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.io.Compressor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext

import java.lang.reflect.Type
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

@CompileStatic
class CompilationOutputsUploader {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>()
  private static final String SOURCES_STATE_FILE_NAME = "target_sources_state.json"
  private static final List<String> PRODUCTION_TYPES = ["java-production", "resources-production"]
  private static final List<String> TEST_TYPES = ["java-test", "resources-test"]
  private static final String IDENTIFIER = "\$PROJECT_DIR\$"
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"
  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String commitHash
  private final Type myTokenType
  private final Gson gson

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, String commitHash, String agentPersistentStorage) {
    this.agentPersistentStorage = agentPersistentStorage
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.commitHash = commitHash
    this.context = context
    gson = new Gson()

    myTokenType = new TypeToken<Map<String, Map<String, BuildTargetState>>>() {}.getType()
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
      def sourceStateFile = new File(dataStorageRoot, SOURCES_STATE_FILE_NAME)
      if (!sourceStateFile.exists()) {
        context.messages.
          warning("Compilation outputs doesn't contain source state file, please enable 'org.jetbrains.jps.portable.caches' flag")
        return
      }
      Map<String, Map<String, BuildTargetState>> currentSourcesState = (Map<String, Map<String, BuildTargetState>>)gson
        .fromJson(FileUtil.loadFile(sourceStateFile, CharsetToolkit.UTF8), myTokenType)

      executor.submit {
        // Upload jps caches started first because of the significant size of the output
        def sourcePath = "caches/$commitHash"
        if (uploader.isExist(sourcePath)) return
        File zipFile = new File(dataStorageRoot.parent, commitHash)
        zipBinaryData(zipFile, dataStorageRoot)
        uploader.upload(sourcePath, zipFile)
        FileUtil.delete(zipFile)

        // Upload compilation metadata
        sourcePath = "metadata/$commitHash"
        if (uploader.isExist(sourcePath)) return
        uploader.upload(sourcePath, sourceStateFile)
        return
      }

      def projectHome = new File(context.paths.projectHome)
      uploadCompilationOutputs(projectHome, currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))

      // Publish metadata file
      def metadataFile = new File("$agentPersistentStorage/metadata.json")
      Files.copy(sourceStateFile, metadataFile)
      messages.artifactBuilt(metadataFile.absolutePath)
    }
    finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  def uploadCompilationOutputs(File root, Map<String, Map<String, BuildTargetState>> currentSourcesState,
                               JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    currentSourcesState.each { type, map ->
      if (PRODUCTION_TYPES.contains(type) || TEST_TYPES.contains(type)) return
      map.each { name, state ->
        def outputPath = state.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

        uploadCompilationOutput(name, type, state.hash, new File(outputPath), uploader, executor)
      }
    }

    uploadCompilationOutputsByParams(PRODUCTION, PRODUCTION_TYPES[0], PRODUCTION_TYPES[1], root, currentSourcesState, uploader, executor)
    uploadCompilationOutputsByParams(TEST, TEST_TYPES[0], TEST_TYPES[1], root, currentSourcesState, uploader, executor)
  }

  private void uploadCompilationOutputsByParams(String prefix, String firstUploadParam, String secondUploadParam, File root,
                                               Map<String, Map<String, BuildTargetState>> currentSourcesState,
                                               JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    def firstParamMap = currentSourcesState.get(firstUploadParam)
    def secondParamMap = currentSourcesState.get(secondUploadParam)

    def firstParamKeys = new HashSet<>(firstParamMap.keySet())
    def secondParamKeys = new HashSet<>(secondParamMap.keySet())
    def intersection = firstParamKeys.intersect(secondParamKeys)

    intersection.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      def hash = calculateStringHash(firstParamState.hash + secondParamState.hash)
      uploadCompilationOutput(buildTargetName, prefix, hash, new File(outputPath), uploader, executor)
    }

    firstParamKeys.removeAll(intersection)
    firstParamKeys.each { buildTargetName ->
      def firstParamState = firstParamMap.get(buildTargetName)
      def outputPath = firstParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      uploadCompilationOutput(buildTargetName, firstUploadParam, firstParamState.hash, new File(outputPath), uploader, executor)
    }

    secondParamKeys.removeAll(intersection)
    secondParamKeys.each { buildTargetName ->
      def secondParamState = secondParamMap.get(buildTargetName)
      def outputPath = secondParamState.relativePath.replace(IDENTIFIER, root.getAbsolutePath())

      uploadCompilationOutput(buildTargetName, secondUploadParam, secondParamState.hash, new File(outputPath), uploader, executor)
    }
  }

  private void uploadCompilationOutput(String buildTargetName, String prefix, String hash, File outputFolder,
                                       JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    executor.submit {
      def sourcePath = "$prefix/$buildTargetName/$hash"
      if (uploader.isExist(sourcePath)) return
      File zipFile = new File(outputFolder.getParent(), hash)
      zipBinaryData(zipFile, outputFolder)
      uploader.upload(sourcePath, zipFile)
      FileUtil.delete(zipFile)
    }
  }

  private static String calculateStringHash(String content) {
    MessageDigest md = messageDigest
    md.reset()
    return StringUtil.toHexString(md.digest(content.getBytes()))
  }

  private static MessageDigest getMessageDigest() throws IOException {
    MessageDigest messageDigest = MESSAGE_DIGEST_THREAD_LOCAL.get()
    if (messageDigest != null) return messageDigest
    messageDigest = MessageDigest.getInstance("MD5")
    MESSAGE_DIGEST_THREAD_LOCAL.set(messageDigest)
    return messageDigest
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

    boolean upload(@NotNull final String path, @NotNull final File file) {
      return super.upload(path, file, false)
    }
  }

  private class BuildTargetState {
    private final String hash
    private final String relativePath

    private BuildTargetState(String hash, String relativePath) {
      this.hash = hash
      this.relativePath = relativePath
    }
  }
}
