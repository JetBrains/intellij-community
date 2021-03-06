// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.google.gson.Gson
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.GZIPInputStream
import java.util.zip.ZipFile

/**
 * Validates that paths specified in product-info.json file are correct
 */
@CompileStatic
class ProductInfoValidator {
  private final BuildContext context

  ProductInfoValidator(BuildContext context) {
    this.context = context
  }

  /**
   * Checks that product-info.json file located in {@code archivePath} archive in {@code pathInArchive} subdirectory is correct
   */
  static void checkInArchive(BuildContext context, String archivePath, String pathInArchive) {
    String productJsonPath = joinPaths(pathInArchive, ProductInfoGenerator.FILE_NAME)

    Path archiveFile = Paths.get(archivePath)
    String entryData = loadEntry(archiveFile, productJsonPath)
    if (entryData == null) {
      context.messages.error("Failed to validate product-info.json: cannot find '$productJsonPath' in $archivePath")
    }
    validateProductJson(context, entryData, archiveFile, "", Collections.<String>emptyList(), List.of(new Pair<>(archivePath, pathInArchive)))
  }

  /**
   * Checks that product-info.json file located in {@code directoryWithProductJson} directory is correct
   * @param installationDirectories directories which will be included into product installation
   * @param installationArchives archives which will be unpacked and included into product installation (the first part specified path to archive,
   * the second part specifies path inside archive)
   */
  void validateInDirectory(Path directoryWithProductJson, String relativePathToProductJson, List<String> installationDirectories,
                           List<Pair<String, String>> installationArchives) {
    Path productJsonFile = directoryWithProductJson.resolve(relativePathToProductJson + ProductInfoGenerator.FILE_NAME)

    String string
    try {
      string = Files.readString(productJsonFile)
    }
    catch (NoSuchFileException ignored) {
      context.messages.error("Failed to validate product-info.json: $productJsonFile doesn't exist")
      return
    }
    validateProductJson(context, string, productJsonFile, relativePathToProductJson, installationDirectories, installationArchives)
  }

  private static void validateProductJson(BuildContext context,
                                          String jsonText, Path productJsonFile,
                                          String relativePathToProductJson,
                                          List<String> installationDirectories,
                                          List<Pair<String, String>> installationArchives) {
    ProductInfoData productJson
    try {
      productJson = new Gson().fromJson(jsonText, ProductInfoData.class)
    }
    catch (Exception e) {
      context.messages.error("Failed to parse product-info.json at $productJsonFile: $e.message", e)
      return
    }

    for (it in productJson.launch) {
      checkFileExists(context, it.launcherPath, "$it.os launcher", relativePathToProductJson, installationDirectories, installationArchives)
      checkFileExists(context, it.javaExecutablePath, "$it.os java executable", relativePathToProductJson,
                      installationDirectories, installationArchives)
      checkFileExists(context, it.vmOptionsFilePath, "$it.os VM options file", relativePathToProductJson,
                      installationDirectories, installationArchives)
    }
  }

  private static void checkFileExists(BuildContext context,
                                      String path,
                                      String description,
                                      String relativePathToProductJson,
                                      List<String> installationDirectories,
                                      List<Pair<String, String>> installationArchives) {
    if (path == null) {
      return
    }

    String pathFromProductJson = relativePathToProductJson + path
    if (!installationDirectories.any { new File(it, pathFromProductJson).exists() } && !installationArchives.any { archiveContainsEntry(it.first, joinPaths(it.second, pathFromProductJson)) }) {
      context.messages.error("Incorrect path to $description '$path' in $relativePathToProductJson/product-info.json: the specified file doesn't exist in directories $installationDirectories " +
                             "and archives ${installationArchives.collect { "$it.first/$it.second" }}")
    }
  }

  private static String joinPaths(String parent, String child) {
    return FileUtil.toCanonicalPath("$parent/$child", '/' as char).dropWhile { it == '/' as char }
  }

  static boolean archiveContainsEntry(String archivePath, String entryPath) {
    File archiveFile = new File(archivePath)
    if (archiveFile.name.endsWith(".zip")) {
      return new ZipFile(archiveFile).withCloseable {
        it.getEntry(entryPath) != null
      }
    }

    if (archiveFile.name.endsWith(".tar.gz")) {
      return archiveFile.withInputStream {
        TarInputStream inputStream = new TarInputStream(new GZIPInputStream(it))
        TarEntry entry
        String altEntryPath = "./$entryPath"
        while (null != (entry = inputStream.nextEntry)) {
          if (entry.name == entryPath || entry.name == altEntryPath) {
            return true
          }
        }
        return false
      }
    }
    return false
  }

  private static @Nullable String loadEntry(Path archiveFile, String entryPath) {
    if (archiveFile.fileName.toString().endsWith(".zip")) {
      ZipFile zipFile = new ZipFile(archiveFile.toFile())
      try {
        InputStream inputStream = zipFile.getInputStream(zipFile.getEntry(entryPath))
        return inputStream == null ? null : new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
      }
      finally {
        zipFile.close()
      }
    }
    else if (archiveFile.fileName.toString().endsWith(".tar.gz")) {
      TarInputStream inputStream = new TarInputStream(new GZIPInputStream(Files.newInputStream(archiveFile)))
      try {
        TarEntry entry
        while (null != (entry = inputStream.getNextEntry())) {
          if (entry.name == entryPath) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
          }
        }
      }
      finally {
        inputStream.close()
      }
    }
    return null
  }
}
