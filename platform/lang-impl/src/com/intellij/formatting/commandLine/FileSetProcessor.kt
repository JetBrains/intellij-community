// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting.commandLine

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.io.IOException
import java.nio.charset.Charset


private var LOG = Logger.getInstance(FileSetProcessor::class.java)


abstract class FileSetProcessor(
  val messageOutput: MessageOutput,
  val isRecursive: Boolean,
  val charset: Charset? = null
) {

  private val topEntries = arrayListOf<File>()
  private val fileMasks = arrayListOf<Regex>()

  protected val statistics = FileSetProcessingStatistics()

  val total: Int
    get() = statistics.getTotal()

  val processed: Int
    get() = statistics.getProcessed()

  val succeeded: Int
    get() = statistics.getValid()

  fun addEntry(filePath: String) = addEntry(File(filePath))

  fun addEntry(file: File) =
    file
      .takeIf { it.exists() }
      ?.let { topEntries.add(it) }
    ?: throw IOException("File $file not found.")

  fun addFileMask(mask: Regex) = fileMasks.add(mask)

  private fun File.matchesFileMask() =
    fileMasks.isEmpty() || fileMasks.any { mask -> mask.matches(name) }

  private fun File.toVirtualFile() =
    LocalFileSystem.getInstance().refreshAndFindFileByIoFile(this) ?: throw IOException("Can not find $path")

  fun processFiles() = topEntries.forEach { entry ->
    entry
      .walkTopDown()
      .maxDepth(if (isRecursive) Int.MAX_VALUE else 1)
      .onEnter {
        LOG.info("Scanning directory ${it.path}")
        true
      }
      .filter { it.isFile }
      .filter { it.matchesFileMask() }
      .map { ioFile -> ioFile.toVirtualFile() }
      .onEach { vFile -> charset?.let { vFile.charset = it } }
      .forEach { vFile ->
        LOG.info("Processing ${vFile.path}")
        statistics.fileTraversed()
        processVirtualFile(vFile)
      }
  }

  abstract fun processVirtualFile(virtualFile: VirtualFile)

  fun getFileMasks() = fileMasks.toList()
  fun getEntries() = topEntries.toList()

}
