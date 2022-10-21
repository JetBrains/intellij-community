// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor

interface FileEditorComposite {
  val allEditors: List<FileEditor>
  val allProviders: List<FileEditorProvider>
  val isPreview: Boolean
}
