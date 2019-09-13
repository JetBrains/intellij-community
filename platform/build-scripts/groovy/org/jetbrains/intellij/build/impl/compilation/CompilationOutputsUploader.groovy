// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.io.Compressor
import groovy.io.FileType
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.compilation.CompilationPartsUploader
import org.jetbrains.intellij.build.impl.compilation.NamedThreadPoolExecutor
import org.jetbrains.jps.model.impl.JpsFileTypesConfigurationImpl
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

import javax.xml.bind.DatatypeConverter
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE

@CompileStatic
class CompilationOutputsUploader {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final byte CARRIAGE_RETURN_CODE = 13
  private static final int HASH_SIZE_IN_BYTES = 16
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"
  private final JpsCompilationPartsUploader uploader
  private final IgnoredPatternSet ignoredPatterns
  private final NamedThreadPoolExecutor executor
  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String commitHash

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, String commitHash, String agentPersistentStorage) {
    this.uploader = new JpsCompilationPartsUploader(remoteCacheUrl, context.messages)
    this.agentPersistentStorage = agentPersistentStorage
    this.messages = context.messages
    this.commitHash = commitHash
    this.context = context

    JpsFileTypesConfigurationImpl configuration = new JpsFileTypesConfigurationImpl()
    ignoredPatterns = new IgnoredPatternSet()
    ignoredPatterns.setIgnoreMasks(configuration.getIgnoredPatternString())

    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()
  }

  def upload() {
    Map<String, String> hashes = new ConcurrentHashMap<String, String>(2048)

    def start = System.currentTimeMillis()
    executor.submit {
      // Upload jps caches started first because of the significant size of the output
      def sourcePath = "caches/$commitHash"
      if (uploader.isExist(sourcePath)) return
      def dataStorageRoot = context.compilationData.dataStorageRoot
      File zipFile = new File(dataStorageRoot.parent, commitHash)
      zipBinaryData(zipFile, dataStorageRoot)
      uploader.upload(sourcePath, zipFile)
      FileUtil.delete(zipFile)
      return
    }

    // Upload production classes output
    def productionModules = new File("$context.paths.buildOutputRoot/classes/$PRODUCTION")
    def moduleFolders = productionModules.listFiles()
    if (moduleFolders == null) {
      context.messages.warning("Production output is empty")
    }
    else {
      uploadCompilationOutputs(productionModules, moduleFolders, PRODUCTION, hashes) { JpsModule module -> getSourcesHash(module, SOURCE, RESOURCE) }
    }

    // Upload test classes output
    def testModules = new File("$context.paths.buildOutputRoot/classes/$TEST")
    moduleFolders = testModules.listFiles()
    if (moduleFolders == null) {
      context.messages.warning("Test output is empty")
    }
    else {
      uploadCompilationOutputs(testModules, moduleFolders, TEST, hashes) { JpsModule module -> getSourcesHash(module, TEST_SOURCE, TEST_RESOURCE) }
    }

    executor.waitForAllComplete(messages)
    executor.reportErrors(messages)
    executor.close()
    messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(System.currentTimeMillis() - start))
    StreamUtil.closeStream(uploader)

    // Save and publish metadata file
    JpsOutputMetadata metadata = new JpsOutputMetadata()
    metadata.commitHash = commitHash;
    metadata.files = new TreeMap<String, String>(hashes)
    String metadataJson = new Gson().toJson(metadata)

    def metadataFile = new File("$agentPersistentStorage/metadata.json")
    FileUtil.writeToFile(metadataFile, metadataJson)
    messages.artifactBuilt(metadataFile.absolutePath)
  }

  def uploadCompilationOutputs(File root, File[] moduleFolders, String prefix, Map<String, String> hashes,
                               Closure<String> calculateModuleHash) {
    moduleFolders.each { File moduleFolder ->
      executor.submit {
        def module = context.findModule(moduleFolder.name)
        def sourcesHash = calculateModuleHash(module)

        def moduleName = "$prefix/$module.name".toString()
        hashes.put(moduleName, sourcesHash)

        def sourcePath = "binaries/$module.name/$prefix/$sourcesHash"
        if (uploader.isExist(sourcePath)) return
        File zipFile = new File(root, sourcesHash)
        zipBinaryData(zipFile, moduleFolder);
        uploader.upload(sourcePath, zipFile)
        FileUtil.delete(zipFile)
      }
    }
  }

  @CompileDynamic
  private String getSourcesHash(JpsModule module, JavaSourceRootType sourceRootType, JavaResourceRootType resourceRootType) {
    def moduleHash = new byte[HASH_SIZE_IN_BYTES];
    Stream.concat(module.getSourceRoots(sourceRootType).toList().stream().map { it.file },
                  module.getSourceRoots(resourceRootType).toList().stream().map { it.file })
      .collect(Collectors.toList())
      .each { folder ->
        if (!folder.exists()) return
        folder.eachFileRecurse(FileType.FILES) { file ->
          if (ignoredPatterns.isIgnored(file.getName())) return
          def fileHash = hashFile(file, folder)
          if (fileHash == null) return
          sum(moduleHash, fileHash)
        }
      }

    return !Arrays.equals(moduleHash, new byte[HASH_SIZE_IN_BYTES]) ? DatatypeConverter.printHexBinary(moduleHash).toLowerCase() : ""
  }

  private byte[] hashFile(File file, File rootPath) {
    try {
      byte[] fileNameBytes = toRelative(file, rootPath).getBytes()
      byte[] bytes = readAllBytesWithoutCarriageReturnChar(file, fileNameBytes)
      return messageDigest.digest(bytes);
    }
    catch (IOException e) {
      context.messages.error("Error while hashing file $file.absolutePath", e)
      return null;
    }
  }

  private static byte[] readAllBytesWithoutCarriageReturnChar(@NotNull File file, @NotNull byte[] fileNameBytes) throws IOException {
    byte[] fileBytes = Files.readAllBytes(file.toPath())
    byte[] result = new byte[fileBytes.length + fileNameBytes.length]
    int copiedBytes = 0
    for (byte fileNameByte : fileNameBytes) {
      result[copiedBytes] = fileNameByte
      copiedBytes++
    }
    for (byte fileByte : fileBytes) {
      if (fileByte != CARRIAGE_RETURN_CODE) {
        result[copiedBytes] = fileByte
        copiedBytes++
      }
    }
    return copiedBytes != result.length ? Arrays.copyOf(result, result.length - (result.length - copiedBytes)) : result
  }

  private static void sum(byte[] firstHash, byte[] secondHash) {
    for (int i = 0; i < firstHash.length; ++i) {
      firstHash[i] = (byte)(firstHash[i] + secondHash[i]);
    }
  }

  private static String toRelative(File target, File rootPath) {
    return FileUtilRt.toSystemIndependentName(Paths.get(rootPath.getPath()).relativize(Paths.get(target.getPath())).toString())
  }

  private void zipBinaryData(File zipFile, File dir) {
    new Compressor.Zip(zipFile).withCloseable { zip ->
      try {
        zip.addDirectory(dir)
      } catch (IOException e) {
        context.messages.error("Couldn't compress binary data: $dir", e)
      }
    }
  }

  private static MessageDigest getMessageDigest() throws IOException {
    MessageDigest messageDigest = MESSAGE_DIGEST_THREAD_LOCAL.get();
    if (messageDigest != null) return messageDigest;
    messageDigest = MessageDigest.getInstance("MD5");
    MESSAGE_DIGEST_THREAD_LOCAL.set(messageDigest);
    return messageDigest;
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

  @CompileStatic
  private static class JpsOutputMetadata {
    String commitHash
    Map<String, String> files
    private JpsOutputMetadata() {}
  }
}
