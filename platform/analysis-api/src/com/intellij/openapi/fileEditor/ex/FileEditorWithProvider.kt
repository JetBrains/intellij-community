// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileEditor.ex

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider

/**
 * A holder for both [FileEditor] and [FileEditorProvider].
 * The package is suffixed with 'ex' for backward compatibility
 */
data class FileEditorWithProvider(val fileEditor: FileEditor, val provider: FileEditorProvider)
