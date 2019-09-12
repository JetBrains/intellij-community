// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl

import com.intellij.openapi.fileTypes.impl.IgnoredPatternSet
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.io.Compressor
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.jps.model.impl.JpsFileTypesConfigurationImpl
import org.jetbrains.jps.model.java.JavaResourceRootType
import org.jetbrains.jps.model.java.JavaSourceRootType
import org.jetbrains.jps.model.module.JpsModule

import javax.xml.bind.DatatypeConverter
import java.nio.file.Files
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.stream.Collectors
import java.util.stream.Stream

import static org.jetbrains.jps.model.java.JavaResourceRootType.RESOURCE
import static org.jetbrains.jps.model.java.JavaResourceRootType.TEST_RESOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.SOURCE
import static org.jetbrains.jps.model.java.JavaSourceRootType.TEST_SOURCE

@CompileStatic
class JpsCompilationOutputUploader {
  private static final int HASH_SIZE_IN_BYTES = 16;
  private static final byte CARRIAGE_RETURN_CODE = 13
  private static final String BINARIES = "binaries"
  private static final String CACHES = "caches"
  private static final String PRODUCTION = "production"
  private static final String TEST = "test"
  private final CompilationPartsUploader uploader
  private final IgnoredPatternSet ignoredPatterns
  private final MessageDigest messageDigest
  private final CompilationContext context
  private final String commitHash

  JpsCompilationOutputUploader(CompilationContext context, MessageDigest messageDigest, String remoteCacheUrl, String commitHash) {
    this.context = context
    this.messageDigest = messageDigest
    this.commitHash = commitHash
    JpsFileTypesConfigurationImpl configuration = new JpsFileTypesConfigurationImpl()
    ignoredPatterns = new IgnoredPatternSet()
    ignoredPatterns.setIgnoreMasks(configuration.getIgnoredPatternString())
    uploader = new CompilationPartsUploader(remoteCacheUrl, context.messages)
  }

  def upload() {
    BuildMessages messages = context.messages
    messages.block("Upload production outputs") {
      def productionModules = new File("$context.paths.buildOutputRoot/classes/$PRODUCTION")
      def moduleFolders = productionModules.listFiles()
      if (moduleFolders == null) {
        context.messages.warning("Production output is empty")
      } else {
        uploadCompilationOutputs(productionModules, moduleFolders, PRODUCTION) { JpsModule module -> getSourcesHash(module, SOURCE, RESOURCE) }
      }
    }

    messages.block("Upload test outputs") {
      def testModules = new File("$context.paths.buildOutputRoot/classes/$TEST")
      def moduleFolders = testModules.listFiles()
      if (moduleFolders == null) {
        context.messages.warning("Test output is empty")
      } else {
        uploadCompilationOutputs(testModules, moduleFolders, TEST) { JpsModule module -> getSourcesHash(module, TEST_SOURCE, TEST_RESOURCE) }
      }
    }

    messages.block("Upload JPS caches") {
      def dataStorageRoot = context.compilationData.dataStorageRoot
      File zipFile = new File(dataStorageRoot.parent, commitHash);
      zipBinaryData(zipFile, dataStorageRoot);
      uploader.upload("$CACHES/$commitHash", zipFile, true)
      FileUtil.delete(zipFile);
    }
  }

  def uploadCompilationOutputs(File root, File[] moduleFolders, String prefix, Closure<String> calculateModuleHash) {
    for (File moduleFolder : moduleFolders) {
      def module = context.findModule(moduleFolder.name)
      def sourcesHash = calculateModuleHash(module)
      File zipFile = new File(root, sourcesHash)
      zipBinaryData(zipFile, moduleFolder);
      uploader.upload("$BINARIES/$module.name/$prefix/$sourcesHash", zipFile, true)
      FileUtil.delete(zipFile);
    }
  }

  @CompileDynamic
  private String getSourcesHash(JpsModule module, JavaSourceRootType sourceRootType, JavaResourceRootType resourceRootType) {
    def rootsHash = new byte[HASH_SIZE_IN_BYTES];
    Stream.concat(module.getSourceRoots(sourceRootType).toList().stream().map { it.file },
                              module.getSourceRoots(resourceRootType).toList().stream().map { it.file })
      .collect(Collectors.toList())
      .collect { hashDirectory(it, it) }
      .each { sum(rootsHash, it) }

    return !Arrays.equals(rootsHash, new byte[HASH_SIZE_IN_BYTES]) ? DatatypeConverter.printHexBinary(rootsHash).toLowerCase() : ""
  }

  private byte[] hashDirectory(File dir, File rootPath) {
    List<File> filesList = Arrays.stream(Optional.ofNullable(dir.listFiles()).orElse(new File[0]))
      .filter { file -> !ignoredPatterns.isIgnored(file.getName()) }.collect(Collectors.toList())

    byte[] hash = new byte[HASH_SIZE_IN_BYTES];
    if (filesList.size() == 1 && filesList.get(0).getName().endsWith(".iml")) return hash
    for (File file : filesList) {
      byte[] curHash = file.isDirectory() ? hashDirectory(file, rootPath) : hashFile(file, rootPath)
      if (curHash == null) continue
      sum(hash, curHash);
    }

    return hash
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
}
