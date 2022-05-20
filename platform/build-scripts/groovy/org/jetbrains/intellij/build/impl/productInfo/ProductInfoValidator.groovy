// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.jr.ob.JSON
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.io.FileUtil
import com.networknt.schema.JsonSchema
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import com.networknt.schema.ValidationMessage
import groovy.transform.CompileStatic
import org.jetbrains.annotations.NotNull
import org.jetbrains.annotations.Nullable
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.OsFamily
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
    String productJsonPath = joinPaths(pathInArchive, ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME)
    byte[] entryData = ArchiveUtils.loadEntry(archiveFile, productJsonPath)
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
    Path productJsonFile = directoryWithProductJson.resolve(relativePathToProductJson + ProductInfoGeneratorKt.PRODUCT_INFO_FILE_NAME)

    byte[] content
    try {
      content = Files.readAllBytes(productJsonFile)
    }
    catch (NoSuchFileException ignored) {
      context.messages.error("Failed to validate product-info.json: $productJsonFile doesn't exist")
      return
    }
    validateProductJson(context, content, productJsonFile, relativePathToProductJson, installationDirectories, installationArchives)
  }

  void validateInDirectory(byte[] productJson, String relativePathToProductJson, List<Path> installationDirectories,
                           List<Pair<Path, String>> installationArchives) {
    validateProductJson(context, productJson, null, relativePathToProductJson, installationDirectories, installationArchives)
  }

  private static void validateProductJson(BuildContext context,
                                          byte[] jsonText,
                                          @Nullable Path productJsonFile,
                                          String relativePathToProductJson,
                                          List<Path> installationDirectories,
                                          List<Pair<Path, String>> installationArchives) {

    Path schemaPath = context.paths.communityHomeDir.resolve("platform/build-scripts/groovy/org/jetbrains/intellij/build/product-info.schema.json")
    verifyJsonBySchema(jsonText, schemaPath, context.messages)

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

    checkFileExists(context, productJson.svgIconPath, "svg icon", relativePathToProductJson, installationDirectories, installationArchives)

    for (launch in productJson.launch) {
      if (OsFamily.ALL.find { (it.osName == launch.os) } == null) {
        context.messages.error("Incorrect os name '$launch.os' in $relativePathToProductJson/product-info.json")
      }

      checkFileExists(context, launch.launcherPath, "$launch.os launcher", relativePathToProductJson, installationDirectories, installationArchives)
      checkFileExists(context, launch.javaExecutablePath, "$launch.os java executable", relativePathToProductJson,
                      installationDirectories, installationArchives)
      checkFileExists(context, launch.vmOptionsFilePath, "$launch.os VM options file", relativePathToProductJson,
                      installationDirectories, installationArchives)
    }
  }

  private static void verifyJsonBySchema(@NotNull byte[] jsonData, @NotNull Path jsonSchemaFile, BuildMessages messages) {
    JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7)
    JsonSchema schema = factory.getSchema(Files.readString(jsonSchemaFile))

    ObjectMapper mapper = new ObjectMapper()
    JsonNode node = mapper.readTree(jsonData)

    Set<ValidationMessage> errors = schema.validate(node)
    if (!errors.isEmpty()) {
      messages.error("Unable to validate JSON agains $jsonSchemaFile:\n" +
                     errors.join("\n") +
                     "\njson file content:\n" + new String(jsonData, StandardCharsets.UTF_8))
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
