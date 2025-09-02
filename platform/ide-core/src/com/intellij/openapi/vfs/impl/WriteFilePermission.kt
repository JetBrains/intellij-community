// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.components.service
import com.intellij.platform.ide.core.permissions.Permission
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class WriteFilePermission(val file: VirtualFile) : Permission {
  override val id: String
    get() = "w: " + file.path

  override fun isGranted(): Boolean {
    if (!file.isWritable) return false

    return service<FilePermissionManager>().isWriteGranted(file)
  }
}
