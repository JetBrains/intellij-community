// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.google.gson.Gson
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.CharsetToolkit
import groovy.transform.CompileStatic
import org.apache.tools.tar.TarEntry
import org.apache.tools.tar.TarInputStream
import org.jetbrains.intellij.build.BuildContext

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

    validateProductJson(loadEntry(archivePath, productJsonPath), archivePath, "", [], [Pair.create(archivePath, pathInArchive)])
  }

  /**
   * Checks that product-info.json file located in {@code directoryWithProductJson} directory is correct
   * @param installationDirectories directories which will be included into product installation
   * @param installationArchives archives which will be unpacked and included into product installation (the first part specified path to archive,
   * the second part specifies path inside archive)
   */
  void validateInDirectory(String directoryWithProductJson, String relativePathToProductJson, List<String> installationDirectories,
                           List<Pair<String, String>> installationArchives) {
    def productJsonFile = new File(directoryWithProductJson, relativePathToProductJson + ProductInfoGenerator.FILE_NAME)
    if (!productJsonFile.exists()) {
      context.messages.error("Failed to validate product-info.json: $productJsonFile doesn't exist")
    }

    validateProductJson(productJsonFile.text, productJsonFile.absolutePath, relativePathToProductJson, installationDirectories, installationArchives)
  }

  private void validateProductJson(String jsonText, String presentablePathToProductJson, String relativePathToProductJson, List<String> installationDirectories,
                                   List<Pair<String, String>> installationArchives) {
    ProductInfoData productJson
    try {
      productJson = new Gson().fromJson(jsonText, ProductInfoData.class)
    }
    catch (Exception e) {
      context.messages.error("Failed to parse product-info.json at $presentablePathToProductJson: $e.message")
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

  static String loadEntry(String archivePath, String entryPath) {
    def archiveFile = new File(archivePath)
    if (archiveFile.name.endsWith(".zip")) {
      return new ZipFile(archiveFile).withCloseable {
        it.getInputStream(it.getEntry(entryPath)).text
      }
    }

    if (archiveFile.name.endsWith(".tar.gz")) {
      return archiveFile.withInputStream {
        def inputStream = new TarInputStream(new GZIPInputStream(it))
        TarEntry entry
        while (null != (entry = inputStream.nextEntry)) {
          if (entry.name == entryPath) {
            def output = new ByteArrayOutputStream()
            inputStream.copyEntryContents(output)
            return new String(output.toByteArray(), CharsetToolkit.UTF8_CHARSET)
          }
        }
        return false
      }
    }
    return false
  }
}
