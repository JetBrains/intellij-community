// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.scratch

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus

/**
 * Allows filtering out filetypes shown in the `New Scratch File` popup.
 */
@ApiStatus.Internal
interface ScratchFileTypeFilter {
  fun isProhibited(type: FileType): Boolean

  companion object {
    private val EP_NAME: ExtensionPointName<ScratchFileTypeFilter> = ExtensionPointName("com.intellij.scratchLanguageFilter")

    @JvmStatic
    fun isEnabled(type: FileType): Boolean {
      return EP_NAME.extensionList.all { !it.isProhibited(type) }
    }
  }
}
