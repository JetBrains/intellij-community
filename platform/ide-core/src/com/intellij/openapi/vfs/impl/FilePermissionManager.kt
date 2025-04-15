// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.impl

import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface FilePermissionManager {
  fun isReadGranted(file: VirtualFile): Boolean
  fun isWriteGranted(file: VirtualFile): Boolean
}