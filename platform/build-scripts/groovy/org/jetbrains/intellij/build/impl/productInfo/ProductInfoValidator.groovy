// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.google.gson.Gson
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.jetbrains.intellij.build.BuildContext

import java.nio.charset.StandardCharsets
import java.nio.file.Files
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
  void checkInArchive(String archivePath, String pathInArchive) {
    def productJsonPath = joinPaths(pathInArchive, ProductInfoGenerator.FILE_NAME)
    if (!archiveContainsEntry(archivePath, productJsonPath)) {
      context.messages.error("Failed to validate product-info.json: cannot find '$productJsonPath' in $archivePath")
    }

    Path archiveFile = Paths.get(archivePath)
    validateProductJson(loadEntry(archiveFile, productJsonPath), archiveFile, "", [], [new Pair<>(archivePath, pathInArchive)])
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
    if (!Files.exists(productJsonFile)) {
      context.messages.error("Failed to validate product-info.json: $productJsonFile doesn't exist")
    }

    validateProductJson(Files.readString(productJsonFile), productJsonFile, relativePathToProductJson, installationDirectories, installationArchives)
  }

  private void validateProductJson(String jsonText, Path productJsonFile, String relativePathToProductJson, List<String> installationDirectories,
                                   List<Pair<String, String>> installationArchives) {
    ProductInfoData productJson
    try {
      productJson = new Gson().fromJson(jsonText, ProductInfoData.class)
    }
    catch (Exception e) {
      context.messages.error("Failed to parse product-info.json at $productJsonFile: $e.message", e)
      return
    }

    productJson.launch.each {
      checkFileExists(it.launcherPath, "$it.os launcher", relativePathToProductJson, installationDirectories,
                      installationArchives)
      checkFileExists(it.javaExecutablePath, "$it.os java executable", relativePathToProductJson,
                      installationDirectories, installationArchives)
      checkFileExists(it.vmOptionsFilePath, "$it.os VM options file", relativePathToProductJson,
                      installationDirectories, installationArchives)
    }
  }

  private void checkFileExists(String path, String description, String relativePathToProductJson, List<String> installationDirectories,
                               List<Pair<String, String>> installationArchives) {
    if (path == null) return

    String pathFromProductJson = relativePathToProductJson + path
    if (!installationDirectories.any { new File(it, pathFromProductJson).exists() } && !installationArchives.any { archiveContainsEntry(it.first, joinPaths(it.second, pathFromProductJson)) }) {
      context.messages.error("Incorrect path to $description '$path' in $relativePathToProductJson/product-info.json: the specified file doesn't exist in directories $installationDirectories " +
                             "and archives ${installationArchives.collect { "$it.first/$it.second" }}")
    }
  }

  private static String joinPaths(String parent, String child) {
    FileUtil.toCanonicalPath("$parent/$child", '/' as char).dropWhile { it == '/' as char}
  }

  static boolean archiveContainsEntry(String archivePath, String entryPath) {
    def archiveFile = new File(archivePath)
    if (archiveFile.name.endsWith(".zip")) {
      return new ZipFile(archiveFile).withCloseable {
        it.getEntry(entryPath) != null
      }
    }

    if (archiveFile.name.endsWith(".tar.gz")) {
      return archiveFile.withInputStream {
        def inputStream = new TarInputStream(new GZIPInputStream(it))
        TarEntry entry
        def altEntryPath = "./$entryPath"
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

  private static String loadEntry(Path archiveFile, String entryPath) {
    if (archiveFile.fileName.toString().endsWith(".zip")) {
      return new ZipFile(archiveFile.toFile()).withCloseable {
        it.getInputStream(it.getEntry(entryPath)).text
      }
    }

    if (archiveFile.fileName.toString().endsWith(".tar.gz")) {
      return archiveFile.withInputStream {
        TarInputStream inputStream = new TarInputStream(new GZIPInputStream(it))
        TarEntry entry
        while (null != (entry = inputStream.getNextEntry())) {
          if (entry.name == entryPath) {
            ByteArrayOutputStream output = new ByteArrayOutputStream()
            inputStream.copyEntryContents(output)
            return new String(output.toByteArray(), StandardCharsets.UTF_8)
          }
        }
        return false
      }
    }
    return false
  }
}
