// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import groovy.transform.CompileStatic
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.impl.ArchiveUtils

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path

/**
 * Validates that paths specified in product-info.json file are correct
 */
@CompileStatic
final class ProductInfoValidator {
  private final BuildContext context

  ProductInfoValidator(BuildContext context) {
    this.context = context
  }

  /**
   * Checks that product-info.json file located in {@code archivePath} archive in {@code pathInArchive} subdirectory is correct
   */
  static void checkInArchive(BuildContext context, String archivePath, String pathInArchive) {
    checkInArchive(context, Path.of(archivePath), pathInArchive)
  }

  /**
   * Checks that product-info.json file located in {@code archivePath} archive in {@code pathInArchive} subdirectory is correct
   */
  static void checkInArchive(BuildContext context, Path archiveFile, String pathInArchive) {
    String productJsonPath = joinPaths(pathInArchive, ProductInfoGenerator.FILE_NAME)
    String entryData = ArchiveUtils.loadEntry(archiveFile, productJsonPath)
    if (entryData == null) {
      context.messages.error("Failed to validate product-info.json: cannot find '$productJsonPath' in $archiveFile")
    }
    validateProductJson(context, entryData, archiveFile, "", Collections.<Path>emptyList(), List.of(new Pair<>(archiveFile, pathInArchive)))
  }

  /**
   * Checks that product-info.json file located in {@code directoryWithProductJson} directory is correct
   * @param installationDirectories directories which will be included into product installation
   * @param installationArchives archives which will be unpacked and included into product installation (the first part specified path to archive,
   * the second part specifies path inside archive)
   */
  void validateInDirectory(Path directoryWithProductJson, String relativePathToProductJson, List<Path> installationDirectories,
                           List<Pair<Path, String>> installationArchives) {
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

  void validateInDirectory(byte[] productJson, String relativePathToProductJson, List<Path> installationDirectories,
                           List<Pair<Path, String>> installationArchives) {
    String string = new String(productJson, StandardCharsets.UTF_8)
    validateProductJson(context, string, null, relativePathToProductJson, installationDirectories, installationArchives)
  }

  private static void validateProductJson(BuildContext context,
                                          String jsonText,
                                          @Nullable Path productJsonFile,
                                          String relativePathToProductJson,
                                          List<Path> installationDirectories,
                                          List<Pair<Path, String>> installationArchives) {
    ProductInfoData productJson
    try {
      productJson = JSON.std.beanFrom(ProductInfoData.class, jsonText)
    }
    catch (Exception e) {
      if (productJsonFile == null) {
        context.messages.error("Failed to parse product-info.json: $e.message", e)
      }
      else {
        context.messages.error("Failed to parse product-info.json at $productJsonFile: $e.message", e)
      }
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
                                      List<Path> installationDirectories,
                                      List<Pair<Path, String>> installationArchives) {
    if (path == null) {
      return
    }

    String pathFromProductJson = relativePathToProductJson + path
    if (!installationDirectories.any { Files.exists(it.resolve(pathFromProductJson)) } &&
        !installationArchives.any { ArchiveUtils.archiveContainsEntry(it.first, joinPaths(it.second, pathFromProductJson)) }) {
      context.messages.error("Incorrect path to $description '$path' in $relativePathToProductJson/product-info.json: the specified file doesn't exist in directories $installationDirectories " +
                             "and archives ${installationArchives.collect { "$it.first/$it.second" }}")
    }
  }

  private static String joinPaths(String parent, String child) {
    return FileUtil.toCanonicalPath("$parent/$child", '/' as char).dropWhile { it == '/' as char }
  }
}
