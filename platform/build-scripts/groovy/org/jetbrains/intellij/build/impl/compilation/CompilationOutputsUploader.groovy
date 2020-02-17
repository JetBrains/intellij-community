// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.common.io.Files
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.io.Compressor
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.cache.BuildTargetState
import org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput
import org.jetbrains.intellij.build.impl.compilation.cache.SourcesStateProcessor

import java.util.concurrent.TimeUnit

@CompileStatic
class CompilationOutputsUploader {
  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String commitHash
  private final SourcesStateProcessor sourcesStateProcessor

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, String commitHash, String agentPersistentStorage) {
    this.agentPersistentStorage = agentPersistentStorage
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.commitHash = commitHash
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

      uploadCompilationOutputs(currentSourcesState, uploader, executor)

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))

      // Publish metadata file
      def metadataFile = new File("$agentPersistentStorage/metadata.json")
      Files.copy(sourceStateFile, metadataFile)
      messages.artifactBuilt(metadataFile.absolutePath)
      // Dirty hack to have fresh state of sources on each build. For now there are a couple of bugs in this area IDEA-228483, IDEA-227783
      FileUtil.delete(sourceStateFile)
    }
    finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  def uploadCompilationOutputs(Map<String, Map<String, BuildTargetState>> currentSourcesState,
                               JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    sourcesStateProcessor.getAllCompilationOutputs(currentSourcesState).forEach { CompilationOutput it ->
      uploadCompilationOutput(it, uploader, executor)
    }
  }

  private void uploadCompilationOutput(CompilationOutput compilationOutput, JpsCompilationPartsUploader uploader, NamedThreadPoolExecutor executor) {
    executor.submit {
      def sourcePath = "${compilationOutput.type}/${compilationOutput.name}/${compilationOutput.hash}"
      if (uploader.isExist(sourcePath)) return
      def outputFolder = new File(compilationOutput.path)
      File zipFile = new File(outputFolder.getParent(), compilationOutput.hash)
      zipBinaryData(zipFile, outputFolder)
      uploader.upload(sourcePath, zipFile)
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
}
