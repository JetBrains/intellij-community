// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.components.service

interface FileNavigator {
  companion object {
    @JvmStatic
    fun getInstance(): FileNavigator = service<FileNavigator>()
  }

  fun canNavigate(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun canNavigateToSource(descriptor: OpenFileDescriptor): Boolean = descriptor.file.isValid

  fun navigate(descriptor: OpenFileDescriptor, requestFocus: Boolean)

  fun navigateInEditor(descriptor: OpenFileDescriptor, requestFocus: Boolean): Boolean
}
