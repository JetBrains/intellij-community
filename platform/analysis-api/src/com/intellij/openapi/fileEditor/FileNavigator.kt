// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

interface FileNavigator {
  companion object {
    @JvmStatic
    fun getInstance(): FileNavigator = service<FileNavigator>()
  }

  fun canNavigate(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean)

  @ApiStatus.Experimental
  suspend fun navigateAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean) {
    withContext(Dispatchers.EDT) {
      navigate(descriptor, requestFocus)
    }
  }

  fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean

  @ApiStatus.Experimental
  suspend fun navigateInEditorAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return withContext(Dispatchers.EDT) {
      navigateInEditor(descriptor, requestFocus)
    }
  }
}
