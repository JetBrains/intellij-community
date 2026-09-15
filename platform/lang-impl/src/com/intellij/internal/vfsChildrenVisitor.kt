// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.openapi.vfs.newvfs.ManagingFS
import com.intellij.openapi.vfs.newvfs.persistent.FSRecords
import org.jetbrains.annotations.ApiStatus

/**
 * Walks the file and each child that the VFS holds already.
 * It does not load a child from the disk.
 */
@ApiStatus.Internal
fun visitChildrenInVfsRecursively(file: VirtualFile): Sequence<VirtualFile> = sequence {
  yield(file)
  if (file.isDirectory) {
    val id = (file as? VirtualFileWithId)?.id ?: return@sequence
    val fs = ManagingFS.getInstance()
    val children = FSRecords.getInstance().list(id).children
      .mapNotNull { fs.findFileById(it.id) }
      .filter { it.name != ".DS_Store" }

    for (child in children) {
      yieldAll(visitChildrenInVfsRecursively(child))
    }
  }
}
