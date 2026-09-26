// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus

interface FileNavigator {
  companion object {
    @JvmStatic
    fun getInstance(): FileNavigator = service<FileNavigator>()
  }

  fun canNavigate(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean)

  /**
   * Navigates [descriptor] in [requestedEditor] if it shows the requested file,
   * falling back to a platform-chosen editor otherwise.
   *
   * Pass `null` as [requestedEditor] when no specific editor is provided.
   */
  @ApiStatus.Experimental
  fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean, requestedEditor: Editor?) {
    navigate(descriptor, requestFocus)
  }

  fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean
}
