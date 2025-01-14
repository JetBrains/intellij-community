// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.fasterxml.jackson.databind.ObjectMapper
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.buildData.productInfo.ProductInfoData
import com.networknt.schema.JsonSchemaFactory
import com.networknt.schema.SpecVersion
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.OsFamily
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

/**
 * Checks that 'product-info.json' file located in [archiveFile] archive in [pathInArchive] subdirectory is correct.
 */
internal fun validateProductJson(archiveFile: Path, pathInArchive: String, context: BuildContext) {
  val productJsonPath = joinPaths(pathInArchive, PRODUCT_INFO_FILE_NAME)
  val entryData = loadEntry(archiveFile, productJsonPath) ?: throw RuntimeException("Failed to validate product-info.json: cannot find '${productJsonPath}' in ${archiveFile}")
  validateProductJson(jsonText = entryData.decodeToString(), installationDirectories = emptyList(), installationArchives = listOf(archiveFile to pathInArchive), context)
}

/**
 * Checks that the 'product-info.json' file is correct.
 *
 * @param installationDirectories directories which will be included in the product installation
 * @param installationArchives    archives which will be unpacked and included in the product installation
 * (the first part specifies a path to the archive, the second part - a path inside the archive)
 */
internal fun validateProductJson(jsonText: String, installationDirectories: List<Path>, installationArchives: List<Pair<Path, String>>, context: BuildContext) {
  val schemaPath = context.paths.communityHomeDir.resolve("platform/buildData/resources/product-info.schema.json")
  verifyJsonBySchema(jsonText, schemaPath, context.messages)

  val productJson = jsonEncoder.decodeFromString<ProductInfoData>(jsonText)
  checkFileExists(productJson.svgIconPath, description = "svg icon", installationDirectories, installationArchives)
  for (item in productJson.launch) {
    val os = item.os
    check(OsFamily.ALL.any { it.osName == os }) {
      "Incorrect OS name '${os}' in ${PRODUCT_INFO_FILE_NAME}"
    }
    checkFileExists(item.launcherPath, description = "${os} launcher", installationDirectories, installationArchives)
    checkFileExists(item.javaExecutablePath, description = "${os} java executable", installationDirectories, installationArchives)
    checkFileExists(item.vmOptionsFilePath, description = "${os} VM options file", installationDirectories, installationArchives)
  }
}

private fun verifyJsonBySchema(jsonData: String, jsonSchemaFile: Path, messages: BuildMessages) {
  val schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7).getSchema(Files.readString(jsonSchemaFile))
  val errors = schema.validate(ObjectMapper().readTree(jsonData))
  if (!errors.isEmpty()) {
    messages.error("Unable to validate JSON against ${jsonSchemaFile}:\n${errors.joinToString("\n")}\nfile content:\n${jsonData}")
  }
}

private fun checkFileExists(path: String?, description: String, installationDirectories: List<Path>, installationArchives: List<Pair<Path, String>>) {
  if (path == null) {
    return
  }

  val pathFromProductJson = path
  if (
    installationDirectories.none { Files.exists(it.resolve(pathFromProductJson)) } &&
    installationArchives.none { archiveContainsEntry(it.first, joinPaths(it.second, pathFromProductJson)) }
  ) {
    throw RuntimeException(
      "Incorrect path to ${description} '${path}' in product-info.json:" +
      " the specified file doesn't exist neither in directories ${installationDirectories}" +
      " nor in archives ${installationArchives.map { "${it.first}/${it.second}" }}")
  }
}

private fun joinPaths(parent: String, child: String): String =
  FileUtilRt.toCanonicalPath(/*path =*/ "${parent}/${child}", /*separatorChar =*/ '/', /*removeLastSlash =*/ true).dropWhile { it == '/' }

private fun archiveContainsEntry(archiveFile: Path, entryPath: String): Boolean {
  val fileName = archiveFile.fileName.toString()
  if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
    // don't use ImmutableZipFile - archive maybe more than 2GB
    FileChannel.open(archiveFile, StandardOpenOption.READ).use { channel ->
      ZipFile.Builder().setSeekableByteChannel(channel).get().use {
        return it.getEntry(entryPath) != null
      }
    }
  }
  else if (fileName.endsWith(".tar.gz")) {
    Files.newInputStream(archiveFile).use {
      val inputStream = TarArchiveInputStream(GzipCompressorInputStream(it))
      val altEntryPath = "./$entryPath"
      while (true) {
        val entry = inputStream.nextEntry ?: break
        if (entry.name == entryPath || entry.name == altEntryPath) {
          return true
        }
      }
    }
  }
  return false
}

private fun loadEntry(archiveFile: Path, entryPath: String): ByteArray? {
  val fileName = archiveFile.fileName.toString()
  if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
    // don't use ImmutableZipFile - archive maybe more than 2GB
    FileChannel.open(archiveFile, StandardOpenOption.READ).use { channel ->
      ZipFile.Builder().setSeekableByteChannel(channel).get().use {
        return it.getInputStream(it.getEntry(entryPath)).readAllBytes()
      }
    }
  }
  else if (fileName.endsWith(".tar.gz")) {
    TarArchiveInputStream(GzipCompressorInputStream(Files.newInputStream(archiveFile))).use { inputStream ->
      val altEntryPath = "./$entryPath"
      while (true) {
        val entry = inputStream.nextEntry ?: break
        if (entry.name == entryPath || entry.name == altEntryPath) {
          return inputStream.readAllBytes()
        }
      }
    }
    return null
  }
  else {
    return null
  }
}
