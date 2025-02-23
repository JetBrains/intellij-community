// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.codeInsight.multiverse.CodeInsightContext
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider


internal sealed interface TemporaryProviderStorage {
  fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider?
  fun put(file: VirtualFile, context: CodeInsightContext, fileViewProvider: FileViewProvider?): FileViewProvider?
  fun remove(file: VirtualFile, context: CodeInsightContext): FileViewProvider?
  fun contains(file: VirtualFile, context: CodeInsightContext): Boolean
}

internal class ClassicTemporaryProviderStorage : TemporaryProviderStorage {
  private val threadLocal = ThreadLocal.withInitial { HashMap<VirtualFile, FileViewProvider?>() }
  private val map get() = threadLocal.get()

  override fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider? =
    map[file]

  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean =
    map.contains(file)

  override fun put(file: VirtualFile, context: CodeInsightContext, fileViewProvider: FileViewProvider?): FileViewProvider? =
    map.put(file, fileViewProvider)

  override fun remove(file: VirtualFile, context: CodeInsightContext): FileViewProvider? =
    map.remove(file)
}

internal class MultiverseTemporaryProviderStorage : TemporaryProviderStorage {
  private val threadLocal = ThreadLocal.withInitial { HashMap<FileAndContext, FileViewProvider?>() }
  private val map get() = threadLocal.get()

  override fun get(file: VirtualFile, context: CodeInsightContext): FileViewProvider? =
    map[FileAndContext(file, context)]

  override fun contains(file: VirtualFile, context: CodeInsightContext): Boolean =
    map.contains(FileAndContext(file, context))

  override fun put(file: VirtualFile, context: CodeInsightContext, fileViewProvider: FileViewProvider?): FileViewProvider? =
    map.put(FileAndContext(file, context), fileViewProvider)

  override fun remove(file: VirtualFile, context: CodeInsightContext): FileViewProvider? =
    map.remove(FileAndContext(file, context))
}

private data class FileAndContext(
  private val file: VirtualFile,
  private val context: CodeInsightContext,
)
