// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.writeIntentReadAction
import com.intellij.openapi.components.service
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

interface FileNavigator {
  companion object {
    @JvmStatic
    fun getInstance(): FileNavigator = service<FileNavigator>()
  }

  fun canNavigate(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean)
  suspend fun navigateAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean) {
    withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        navigate(descriptor, requestFocus)
      }
    }
  }

  fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean
  suspend fun navigateInEditorAsync(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean {
    return withContext(Dispatchers.EDT) {
      writeIntentReadAction {
        navigateInEditor(descriptor, requestFocus)
      }
    }
  }
}
