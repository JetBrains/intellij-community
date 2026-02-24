// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lang

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VirtualFileSystemEntry

internal class FileNameMatcherByIdServiceImpl : FileNameMatcherByIdService {
  override fun isFileNameMatches(virtualFile: VirtualFile, name: String): Boolean? {
    val fileNameId = (virtualFile as? VirtualFileSystemEntry)?.nameId ?: return null
    return fileNameId == VirtualFileManager.getInstance().storeName(name)
  }
}