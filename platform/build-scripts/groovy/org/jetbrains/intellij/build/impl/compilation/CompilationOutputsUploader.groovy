// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.google.gson.Gson
import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.openapi.util.io.StreamUtil
import com.intellij.util.io.Compressor
import groovy.io.FileType
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
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE

@CompileStatic
class CompilationOutputsUploader {
  private static final ThreadLocal<MessageDigest> MESSAGE_DIGEST_THREAD_LOCAL = new ThreadLocal<>();
  private static final byte CARRIAGE_RETURN_CODE = 13
  private static final byte LINE_FEED_CODE = 10
  private static final int HASH_SIZE_IN_BYTES = 16
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"
  private final IgnoredPatternSet ignoredPatterns
  private final String agentPersistentStorage
  private final CompilationContext context
  private final BuildMessages messages
  private final String remoteCacheUrl
  private final String commitHash

  CompilationOutputsUploader(CompilationContext context, String remoteCacheUrl, String commitHash, String agentPersistentStorage) {
    this.agentPersistentStorage = agentPersistentStorage
    this.remoteCacheUrl = remoteCacheUrl
    this.messages = context.messages
    this.commitHash = commitHash
    this.context = context

    JpsFileTypesConfigurationImpl configuration = new JpsFileTypesConfigurationImpl()
    ignoredPatterns = new IgnoredPatternSet()
    ignoredPatterns.setIgnoreMasks(configuration.getIgnoredPatternString())
  }

  def upload() {
    JpsCompilationPartsUploader uploader = new JpsCompilationPartsUploader(remoteCacheUrl, context.messages)
    int executorThreadsCount = Runtime.getRuntime().availableProcessors()
    context.messages.info("$executorThreadsCount threads will be used for upload")
    NamedThreadPoolExecutor executor = new NamedThreadPoolExecutor("Jps Output Upload", executorThreadsCount)
    executor.prestartAllCoreThreads()

    try {
      def start = System.nanoTime()
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

      def productionModules = new File("$context.paths.buildOutputRoot/classes/$PRODUCTION")
      def productionFiles = productionModules.listFiles() ?: new File[0]

      def testModules = new File("$context.paths.buildOutputRoot/classes/$TEST")
      def testFiles = testModules.listFiles() ?: new File[0]

      int mapCapacity = productionFiles.length + testFiles.length
      Map<String, String> hashes = new ConcurrentHashMap<String, String>(mapCapacity)

      // Upload production classes output
      uploadCompilationOutputs(productionModules, productionFiles, PRODUCTION, hashes, uploader, executor) {
        JpsModule module -> getSourcesHash(module, SOURCE, RESOURCE)
      }

      // Upload test classes output
      uploadCompilationOutputs(testModules, testFiles, TEST, hashes, uploader, executor) {
        JpsModule module -> getSourcesHash(module, TEST_SOURCE, TEST_RESOURCE)
      }

      executor.waitForAllComplete(messages)
      executor.reportErrors(messages)
      messages.reportStatisticValue("Compilation upload time, ms", String.valueOf(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start)))

      // Save and publish metadata file
      JpsOutputMetadata metadata = new JpsOutputMetadata()
      metadata.commitHash = commitHash;
      metadata.files = new TreeMap<String, String>(hashes)
      String metadataJson = new Gson().toJson(metadata)

      def metadataFile = new File("$agentPersistentStorage/metadata.json")
      FileUtil.writeToFile(metadataFile, metadataJson)
      messages.artifactBuilt(metadataFile.absolutePath)
    } finally {
      executor.close()
      StreamUtil.closeStream(uploader)
    }
  }

  def uploadCompilationOutputs(File root, File[] moduleFolders, String prefix, Map<String, String> hashes, JpsCompilationPartsUploader uploader,
                               NamedThreadPoolExecutor executor, Closure<String> calculateModuleHash) {
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

  private String getSourcesHash(JpsModule module, JavaSourceRootType sourceRootType, JavaResourceRootType resourceRootType) {
    byte[] moduleHash = null
    Stream.concat(module.getSourceRoots(sourceRootType).toList().stream(), module.getSourceRoots(resourceRootType).toList().stream())
      .map { it.file }
      .each { File folder ->
        if (!folder.exists()) return
        folder.eachFileRecurse(FileType.FILES) { file ->
          if (ignoredPatterns.isIgnored(file.getName())) return
          def fileHash = hashFile(file, folder)
          if (fileHash == null) return
          moduleHash = sum(moduleHash, fileHash)
        }
      }

    return moduleHash != null ? DatatypeConverter.printHexBinary(moduleHash).toLowerCase() : ""
  }

  private byte[] hashFile(File file, File rootPath) {
    try {
      MessageDigest md = messageDigest
      md.reset()
      md.update(toRelative(file, rootPath).getBytes())
      new FileInputStream(file).withStream { fis ->
        byte[] buf = new byte[1024 * 1024]
        int length
        while ((length = fis.read(buf)) != -1) {
          byte[] res = new byte[length]
          int copiedBytes = 0
          for (int i = 0; i < length; i++) {
            if (buf[i] != CARRIAGE_RETURN_CODE && ((i + 1) >= length || buf[i + 1] != LINE_FEED_CODE)) {
              res[copiedBytes] = buf[i]
              copiedBytes++
            }
          }
          md.update(copiedBytes != res.length ? Arrays.copyOf(res, res.length - (res.length - copiedBytes)) : res)
        }
      }
      return md.digest()
    }
    catch (IOException e) {
      context.messages.error("Error while hashing file $file.absolutePath", e)
      return null;
    }
  }

  private static byte[] sum(byte[] firstHash, byte[] secondHash) {
    byte[] result = firstHash ?: new byte[HASH_SIZE_IN_BYTES]
    for (int i = 0; i < result.length; ++i) {
      result[i] = (byte)(result[i] + secondHash[i])
    }
    return result
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
