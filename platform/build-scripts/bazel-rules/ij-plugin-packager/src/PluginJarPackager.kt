package com.intellij.tools.build.bazel.ijPluginPackager

import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zipWriter
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.pathString

/**
 * Provides a way to create a plugin jar by including entries from different sources.
 */
internal class PluginJarPackager(private val outputJarPath: Path) : AutoCloseable {
  private val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  private val zipWriter = ZipFileWriter(zipWriter(outputJarPath, packageIndexBuilder))
  /** relative path added to the JAR -> presentable description of its origin */
  private val addedFilePaths = HashMap<String, String>()

  internal fun interface ZipEntryPatcher {
    /**
     * Returns the actual content of the entry with [filePath] to be included in the output jar or `null` if the entry should be skipped
     */
    fun patchEntry(filePath: String, dataFetcher: () -> ByteBuffer): ByteBuffer?
  }

  /** Returns `true` if an entry with [relativePath] was already added to the JAR. */
  fun containsEntry(relativePath: String): Boolean = addedFilePaths.containsKey(relativePath)

  fun addFile(relativePath: String, content: ByteArray, presentableOrigin: String) {
    checkAddedFile(relativePath, presentableOrigin)
    packageIndexBuilder.addFile(relativePath)
    zipWriter.uncompressedData(relativePath, content)
  }

  /**
   * Adds the entries of [inputJar]. [entryPath] gives the path of an entry in the output JAR, or `null` to skip the entry.
   * [entryPatcher] gets the path in the output JAR.
   */
  fun addEntriesFromJar(inputJar: Path, entryPath: (String) -> String? = { it }, entryPatcher: ZipEntryPatcher) {
    readZipFile(inputJar) { sourcePath, dataFetcher ->
      val filePath = entryPath(sourcePath) ?: return@readZipFile ZipEntryProcessorResult.CONTINUE
      val patchedData = entryPatcher.patchEntry(filePath, dataFetcher)
      if (patchedData != null) {
        val origin = if (filePath == sourcePath) inputJar.pathString else "${inputJar.pathString} (renamed from $sourcePath)"
        checkAddedFile(filePath, origin)
        packageIndexBuilder.addFile(filePath)
        zipWriter.uncompressedData(filePath, patchedData)
      }
      ZipEntryProcessorResult.CONTINUE
    }
  }

  private fun checkAddedFile(relativePath: String, presentableOrigin: String) {
    val oldOrigin = addedFilePaths.put(relativePath, presentableOrigin)
    if (oldOrigin != null) {
      throw IjPluginPackagingException(
        "File $relativePath was added twice to $outputJarPath: from $oldOrigin and from $presentableOrigin. " +
        "If it comes from 'packed_deps', rename or skip one entry with the 'packed_deps_entry_renaming' attribute of 'ij_plugin_module'."
      )
    }
  }

  override fun close() {
    zipWriter.close()
  }
}
