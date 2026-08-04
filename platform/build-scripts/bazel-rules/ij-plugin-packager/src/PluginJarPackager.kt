package com.intellij.tools.build.bazel.ijPluginPackager

import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zipWriter
import java.nio.ByteBuffer
import java.nio.file.Path

/**
 * Provides a way to create a plugin jar by including entries from different sources.
 */
internal class PluginJarPackager(outputJarPath: Path) : AutoCloseable {
  private val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  private val zipWriter = ZipFileWriter(zipWriter(outputJarPath, packageIndexBuilder))

  internal fun interface ZipEntryPatcher {
    /**
     * Returns the actual content of the entry with [filePath] to be included in the output jar or `null` if the entry should be skipped
     */
    fun patchEntry(filePath: String, dataFetcher: () -> ByteBuffer): ByteBuffer?
  }

  fun addEntriesFromJar(inputJar: Path, entryPatcher: ZipEntryPatcher) {
    readZipFile(inputJar) { filePath, dataFetcher ->
      val patchedData = entryPatcher.patchEntry(filePath, dataFetcher)
      if (patchedData != null) {
        packageIndexBuilder.addFile(filePath)
        zipWriter.uncompressedData(filePath, patchedData)
      }
      ZipEntryProcessorResult.CONTINUE
    }
  }

  override fun close() {
    zipWriter.close()
  }
}
