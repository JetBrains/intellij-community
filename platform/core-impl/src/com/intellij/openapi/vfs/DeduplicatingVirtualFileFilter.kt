// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import java.util.*


class DeduplicatingVirtualFileFilter(private val delegate: VirtualFileFilter?) : VirtualFileFilter {
  private val visited = BitSet()

  override fun accept(file: VirtualFile): Boolean {
    if (delegate != null) {
      return delegate.accept(file)
    }
    if (file !is VirtualFileWithId) return true

    val fileId = (file as VirtualFileWithId).getId()
    if (fileId <= 0) {
      return true
    }

    val wasVisited = visited.get(fileId)
    visited.set(fileId)
    return !wasVisited
  }
}