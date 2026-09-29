// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.productInfo

import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.platform.buildData.productInfo.ProductInfoData
import com.intellij.platform.buildData.productInfo.ProductInfoLaunchData
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SpecificationVersion
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.archivers.zip.ZipFile
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.intellij.build.BuildContext
import org.jetbrains.intellij.build.BuildMessages
import org.jetbrains.intellij.build.OsFamily
import org.jetbrains.intellij.build.isLanguageServer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.TreeSet
import java.util.concurrent.ConcurrentHashMap

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
  val installation = Installation(installationDirectories, installationArchives)
  if (!context.isLanguageServer) {
    installation.checkFileExists(productJson.svgIconPath, description = "svg icon")
  }
  for (item in productJson.launch) {
    val os = item.os
    check(OsFamily.ALL.any { it.osName == os }) {
      "Incorrect OS name '${os}' in ${PRODUCT_INFO_FILE_NAME}"
    }
    installation.checkFileExists(item.launcherPath, description = "${os} launcher")
    installation.checkFileExists(item.javaExecutablePath, description = "${os} java executable")
    installation.checkFileExists(item.vmOptionsFilePath, description = "${os} VM options file")
    for (directory in nativeDirectoriesOfLaunch(item)) {
      installation.checkDirectoryExists(directory, description = "${os} native library directory")
    }
  }
}

/**
 * The JVM arguments whose value is a directory of native files. The library reads the directory at run time and has
 * no other copy of the files, so the installation must hold it. See `renderAdditionalJvmArguments`.
 */
private val NATIVE_DIRECTORY_ARGUMENTS = listOf("jna.boot.library.path", "pty4j.preferred.native.folder", "skiko.library.path")

/**
 * Maps the home macro of a JVM argument to the installation directory relative to the directory of `product-info.json`.
 * On macOS, `product-info.json` is in `Contents/Resources/`, so `$APP_PACKAGE/Contents` is its parent directory.
 * A language server on macOS keeps `product-info.json` in the package root.
 */
private val HOME_MACROS = listOf(
  $$"$APP_PACKAGE/Contents/" to "../",
  $$"$APP_PACKAGE/" to "",
  $$"$IDE_HOME/" to "",
  "%IDE_HOME%/" to "",
)

/**
 * Returns the directories that the native-library JVM arguments of [launch] and its custom commands name, relative to
 * the directory of `product-info.json`. Fails on an argument without a known home macro.
 */
@ApiStatus.Internal
fun nativeDirectoriesOfLaunch(launch: ProductInfoLaunchData): List<String> {
  val arguments = launch.additionalJvmArguments.asSequence() + launch.customCommands.asSequence().flatMap { it.additionalJvmArguments }
  return arguments.mapNotNull(::nativeDirectoryOfArgument).distinct().toList()
}

private fun nativeDirectoryOfArgument(argument: String): String? {
  val prefix = NATIVE_DIRECTORY_ARGUMENTS.map { "-D$it=" }.firstOrNull { argument.startsWith(it) } ?: return null
  val value = argument.substring(prefix.length)
  val (macro, home) = HOME_MACROS.firstOrNull { value.startsWith(it.first) }
                      ?: throw RuntimeException("The JVM argument '$argument' in $PRODUCT_INFO_FILE_NAME has no home macro, so the directory cannot be checked")
  return home + value.substring(macro.length)
}

/** The files of a product installation: unpacked directories, and archives with the installation below a path. */
private class Installation(private val directories: List<Path>, private val archives: List<Pair<Path, String>>) {
  /** The entry names of each archive, read once. A directory has no entry of its own in a zip the build writes. */
  private val archiveEntries: Map<Path, TreeSet<String>> by lazy { archives.associate { (archive, _) -> archive to archiveEntryNames(archive) } }

  fun checkFileExists(path: String?, description: String) {
    if (path == null) {
      return
    }
    if (directories.none { Files.exists(it.resolve(path)) } && archives.none { archiveHasEntry(it, path) }) {
      fail(path, description)
    }
  }

  fun checkDirectoryExists(path: String, description: String) {
    if (directories.none { Files.isDirectory(it.resolve(path)) } && archives.none { archiveHasEntryUnder(it, path) }) {
      fail(path, description)
    }
  }

  private fun fail(path: String, description: String): Nothing {
    throw RuntimeException(
      "Incorrect path to ${description} '${path}' in product-info.json:" +
      " the specified file doesn't exist neither in directories ${directories}" +
      " nor in archives ${archives.map { "${it.first}/${it.second}" }}")
  }

  private fun archiveHasEntry(archive: Pair<Path, String>, path: String): Boolean {
    val entries = archiveEntries.getValue(archive.first)
    val entryPath = joinPaths(archive.second, path)
    return entries.contains(entryPath) || entries.contains("./$entryPath")
  }

  private fun archiveHasEntryUnder(archive: Pair<Path, String>, path: String): Boolean {
    val entries = archiveEntries.getValue(archive.first)
    val prefix = joinPaths(archive.second, path) + "/"
    return entries.ceiling(prefix)?.startsWith(prefix) == true || entries.ceiling("./$prefix")?.startsWith("./$prefix") == true
  }
}

private val jsonSchemaRegistry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)

/** Compiled once per schema file. `SchemaRegistry.validate` would parse the schema again on every call. */
private val jsonSchemas = ConcurrentHashMap<Path, Schema>()

private fun verifyJsonBySchema(jsonData: String, jsonSchemaFile: Path, messages: BuildMessages) {
  val schema = jsonSchemas.computeIfAbsent(jsonSchemaFile) { jsonSchemaRegistry.getSchema(Files.readString(it)) }
  val errors = schema.validate(jsonData, InputFormat.JSON)
  if (!errors.isEmpty()) {
    messages.logErrorAndThrow("Unable to validate JSON against ${jsonSchemaFile}:\n${errors.joinToString("\n")}\nfile content:\n${jsonData}")
  }
}

private fun joinPaths(parent: String, child: String): String =
  FileUtilRt.toCanonicalPath(/*path =*/ "${parent}/${child}", /*separatorChar =*/ '/', /*removeLastSlash =*/ true).dropWhile { it == '/' }

/** The names of all entries of a zip or a `tar.gz` archive. An archive of another kind has no entries. */
private fun archiveEntryNames(archiveFile: Path): TreeSet<String> {
  val names = TreeSet<String>()
  val fileName = archiveFile.fileName.toString()
  if (fileName.endsWith(".zip") || fileName.endsWith(".jar")) {
    // don't use ImmutableZipFile - archive maybe more than 2GB
    FileChannel.open(archiveFile, StandardOpenOption.READ).use { channel ->
      ZipFile.Builder().setSeekableByteChannel(channel).get().use { zipFile ->
        for (entry in zipFile.entries) {
          names.add(entry.name)
        }
      }
    }
  }
  else if (fileName.endsWith(".tar.gz")) {
    TarArchiveInputStream(GzipCompressorInputStream(Files.newInputStream(archiveFile))).use { inputStream ->
      while (true) {
        val entry = inputStream.nextEntry ?: break
        names.add(entry.name)
      }
    }
  }
  return names
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
