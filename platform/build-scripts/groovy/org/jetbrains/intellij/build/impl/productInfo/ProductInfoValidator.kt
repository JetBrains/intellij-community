// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.io.FileUtilRt
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import kotlinx.serialization.decodeFromString
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.impl.ArchiveUtils
import java.nio.file.Files
import java.nio.file.Path

/**
 * Validates that paths specified in product-info.json file are correct
 */
object ProductInfoValidator {
  /**
   * Checks that product-info.json file located in `archivePath` archive in `pathInArchive` subdirectory is correct
   */
  @JvmStatic
  fun checkInArchive(context: BuildContext, archiveFile: Path, pathInArchive: String) {
    val productJsonPath = joinPaths(pathInArchive, PRODUCT_INFO_FILE_NAME)
    val entryData = ArchiveUtils.loadEntry(archiveFile, productJsonPath)
                    ?: throw RuntimeException("Failed to validate product-info.json: cannot find \'$productJsonPath\' in $archiveFile")
    validateProductJson(context = context,
                        jsonText = entryData,
                        relativePathToProductJson = "",
                        installationDirectories = emptyList(),
                        installationArchives = listOf(Pair(archiveFile, pathInArchive)))
  }

  /**
   * Checks that product-info.json file located in `directoryWithProductJson` directory is correct
   *
   * @param installationDirectories directories which will be included into product installation
   * @param installationArchives    archives which will be unpacked and included into product installation (the first part specified path to archive,
   * the second part specifies path inside archive)
   */
  @JvmStatic
  fun validateInDirectory(directoryWithProductJson: Path,
                          relativePathToProductJson: String,
                          installationDirectories: List<Path>,
                          installationArchives: List<Pair<Path, String>>,
                          context: BuildContext) {
    val productJsonFile = directoryWithProductJson.resolve(relativePathToProductJson + PRODUCT_INFO_FILE_NAME)
    validateProductJson(context, Files.readAllBytes(productJsonFile), relativePathToProductJson, installationDirectories,
                        installationArchives)
  }

  @JvmStatic
  fun validateInDirectory(productJson: ByteArray,
                          relativePathToProductJson: String,
                          installationDirectories: List<Path>,
                          installationArchives: List<Pair<Path, String>>,
                          context: BuildContext) {
    validateProductJson(context, productJson, relativePathToProductJson, installationDirectories, installationArchives)
  }
}

private fun validateProductJson(context: CompilationContext,
                                jsonText: ByteArray,
                                relativePathToProductJson: String,
                                installationDirectories: List<Path>,
                                installationArchives: List<Pair<Path, String>>) {
  val schemaPath = context.paths.communityHomeDir
    .resolve("platform/build-scripts/groovy/org/jetbrains/intellij/build/product-info.schema.json")
  val messages = context.messages
  verifyJsonBySchema(jsonText, schemaPath, messages)
  val productJson = jsonEncoder.decodeFromString<ProductInfoData>(jsonText.toString(Charsets.UTF_8))
  checkFileExists(messages = messages,
                  path = productJson.svgIconPath,
                  description = "svg icon",
                  relativePathToProductJson = relativePathToProductJson,
                  installationDirectories = installationDirectories,
                  installationArchives = installationArchives)
  for ((os, launcherPath, javaExecutablePath, vmOptionsFilePath) in productJson.launch) {
    if (OsFamily.ALL.none { it.osName == os }) {
      messages.error("Incorrect os name \'$os\' in $relativePathToProductJson/product-info.json")
    }
    checkFileExists(messages, launcherPath, "$os launcher", relativePathToProductJson, installationDirectories,
                    installationArchives)
    checkFileExists(messages, javaExecutablePath, "$os java executable", relativePathToProductJson,
                    installationDirectories, installationArchives)
    checkFileExists(messages, vmOptionsFilePath, "$os VM options file", relativePathToProductJson,
                    installationDirectories, installationArchives)
  }
}

private fun verifyJsonBySchema(jsonData: ByteArray, jsonSchemaFile: Path, messages: BuildMessages) {
  val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(Files.readString(jsonSchemaFile))
  val mapper = ObjectMapper()
  val errors = schema.validate(mapper.readTree(jsonData))
  if (!errors.isEmpty()) {
    messages.error("Unable to validate JSON against $jsonSchemaFile:" +
                   "\n${errors.joinToString(separator = "\n")}\njson file content:\n${jsonData.toString(Charsets.UTF_8)}")
  }
}

private fun checkFileExists(messages: BuildMessages,
                            path: String?,
                            description: String,
                            relativePathToProductJson: String,
                            installationDirectories: List<Path>,
                            installationArchives: List<Pair<Path, String>>) {
  if (path == null) {
    return
  }

  val pathFromProductJson = relativePathToProductJson + path
  if (!installationDirectories.any { Files.exists(it.resolve(pathFromProductJson)) } &&
      !installationArchives.any { ArchiveUtils.archiveContainsEntry(it.first, joinPaths(it.second, pathFromProductJson)) }) {
    messages.error("Incorrect path to $description '$path' in $relativePathToProductJson/product-info.json: " +
                   "the specified file doesn't exist in directories $installationDirectories " +
                   "and archives ${installationArchives.map { "$it.first/$it.second" }}")
  }
}

private fun joinPaths(parent: String, child: String): String {
  return FileUtilRt.toCanonicalPath(/* path = */ "$parent/$child", /* separatorChar = */ '/', /* removeLastSlash = */ true)
    .dropWhile { it == '/' }
}