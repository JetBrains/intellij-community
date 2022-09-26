// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

import com.intellij.openapi.util.UserDataHolderBase

abstract class FileEditorComposite : UserDataHolderBase() {
  abstract val allEditors: List<FileEditor>
  abstract val allProviders: List<FileEditorProvider>
  abstract val isPreview: Boolean
}
