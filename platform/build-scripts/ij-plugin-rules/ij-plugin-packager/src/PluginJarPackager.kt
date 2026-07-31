package com.intellij.tools.build.bazel.ijPluginPackager

import org.jetbrains.intellij.build.io.AddDirEntriesMode
import org.jetbrains.intellij.build.io.PackageIndexBuilder
import org.jetbrains.intellij.build.io.ZipEntryProcessorResult
import org.jetbrains.intellij.build.io.ZipFileWriter
import org.jetbrains.intellij.build.io.readZipFile
import org.jetbrains.intellij.build.io.zipWriter
import java.nio.file.Path

internal class PluginJarPackager(outputJarPath: Path) : AutoCloseable {
  private val packageIndexBuilder = PackageIndexBuilder(AddDirEntriesMode.NONE)
  private val zipWriter = ZipFileWriter(zipWriter(outputJarPath, packageIndexBuilder))

  fun addEntriesFromJar(inputJar: Path, filePathFilter: ((String) -> Boolean)? = null) {
    readZipFile(inputJar) { filePath, data ->
      if (filePathFilter == null || filePathFilter(filePath)) {
        packageIndexBuilder.addFile(filePath)
        zipWriter.uncompressedData(filePath, data())
      }
      ZipEntryProcessorResult.CONTINUE
    }
  }

  override fun close() {
    zipWriter.close()
  }
}
