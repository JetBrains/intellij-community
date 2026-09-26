// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl

import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.DocumentSettings

internal class DocumentElfSettingsImpl(
  private val origin: DocumentSettings,
) : DocumentSettings by origin {

  /**
   * Elf document, by definition, should not check write access
   */
  override fun isWriteAccessCheckEnabled(): Boolean {
    return false
  }

  /**
   * Elf document, by definition, should not check write access
   */
  override fun assertWriteAccess(hostDocument: Document) {
  }

  override fun setCycleBufferSize(buffer: Int) {
    if (buffer > 0) {
      // TODO: implement cycle buffer for ElfDocument
      throw UnsupportedOperationException("ElfDocument does not support cycle buffer yet")
    }
  }
}
