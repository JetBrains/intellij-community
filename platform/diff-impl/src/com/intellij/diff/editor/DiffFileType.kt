// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.editor

import com.intellij.icons.AllIcons
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

class DiffFileType private constructor() : FileType {
  override fun getName(): String = "DIFF"
  override fun getDescription(): String = DiffBundle.message("filetype.diff.description")
  override fun getDefaultExtension(): String = ""
  override fun getIcon(): Icon? = AllIcons.Actions.Diff

  override fun isBinary(): Boolean = true
  override fun isReadOnly(): Boolean = true

  companion object {
    val INSTANCE: FileType = DiffFileType()
  }
}
