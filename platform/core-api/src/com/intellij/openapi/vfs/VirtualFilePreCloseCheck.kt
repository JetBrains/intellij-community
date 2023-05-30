// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs

import com.intellij.openapi.extensions.ExtensionPointName

interface VirtualFilePreCloseCheck {
  companion object {
    val extensionPoint: ExtensionPointName<VirtualFilePreCloseCheck> = ExtensionPointName.create<VirtualFilePreCloseCheck>("com.intellij.openapi.vfs.VirtualFilePreCloseCheck")
  }

  /**
   * This method can handle some logic to prevent file from closing e.g. confirmation dialog
   * @return true if the file can be closed, otherwise false.
   */
  fun canCloseFile(file: VirtualFile): Boolean
}