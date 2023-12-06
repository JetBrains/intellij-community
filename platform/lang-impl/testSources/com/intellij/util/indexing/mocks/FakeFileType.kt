// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.mocks

import com.intellij.openapi.fileTypes.FileType
import javax.swing.Icon

class FakeFileType(val extension: String = "3033b4d271c2", val binary: Boolean = false) : FileType {
  override fun getName(): String = "FakeFileType for test"
  override fun getDescription(): String = "FakeFileType for test"
  override fun getDefaultExtension(): String = extension
  override fun getIcon(): Icon? = null
  override fun isBinary(): Boolean = binary
}
