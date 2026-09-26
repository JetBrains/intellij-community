// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.file.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.FileViewProvider

internal sealed interface TemporaryProviderStorage {
  fun get(file: VirtualFile): FileViewProvider?
  fun put(file: VirtualFile, fileViewProvider: FileViewProvider?): FileViewProvider?
  fun remove(file: VirtualFile): FileViewProvider?
  fun contains(file: VirtualFile): Boolean
}

internal fun createTemporaryProviderStorage(): TemporaryProviderStorage = TemporaryProviderStorageImpl()

private class TemporaryProviderStorageImpl : TemporaryProviderStorage {
  private val threadLocal = ThreadLocal.withInitial { HashMap<VirtualFile, FileViewProvider?>() }
  private val map get() = threadLocal.get()

  override fun get(file: VirtualFile): FileViewProvider? =
    map[file]

  override fun contains(file: VirtualFile): Boolean =
    map.contains(file)

  override fun put(file: VirtualFile, fileViewProvider: FileViewProvider?): FileViewProvider? =
    map.put(file, fileViewProvider)

  override fun remove(file: VirtualFile): FileViewProvider? =
    map.remove(file)
}
