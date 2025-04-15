// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Internal

package com.intellij.openapi.editor.rd

import com.intellij.idea.AppModeAssertions
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.isLocalEditorUx
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.fileTypes.FileType
import org.jetbrains.annotations.ApiStatus.Internal

fun assertLocalEditorSupport(editor: Editor) {
  if (editor.isLocalEditorSupport()) {
    AppModeAssertions.assertFrontend(false)
  }
}

fun Editor.isLocalEditorSupport(): Boolean {
  if (!isLocalEditorUx) {
    return false
  }
  val virtualFile = virtualFile
  if (virtualFile == null) {
    return false
  }
  return virtualFile.fileType.isLocalEditorSupport()
}

private fun FileType.isLocalEditorSupport(): Boolean {
  return localEditorSupportBeanFor(this) != null
}

private val EP_NAME = ExtensionPointName<LocalEditorSupportBean>("com.intellij.editor.rd.localSupport")

private fun localEditorSupportBeanFor(fileType: FileType): LocalEditorSupportBean? {
  return EP_NAME.getByKey(fileType.name, LocalEditorSupportBean::class.java, LocalEditorSupportBean::getKey)
}
