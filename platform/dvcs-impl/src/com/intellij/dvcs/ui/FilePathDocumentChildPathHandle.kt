// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.ui.DocumentAdapter
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.event.DocumentEvent
import javax.swing.text.Document

class FilePathDocumentChildPathHandle private constructor(
  private val document: Document,
  private val defaultParentPath: Path
) : DocumentAdapter() {

  private var modifiedByUser = false

  init {
    document.remove(0, document.length)
    document.insertString(0, defaultParentPath.toString(), null)
  }

  override fun textChanged(e: DocumentEvent) {
    modifiedByUser = true
  }

  fun trySetChildPath(child: String) {
    if (!modifiedByUser) {
      try {
        val newPath = defaultParentPath.resolve(child)
        document.remove(0, document.length)
        document.insertString(0, newPath.toString(), null)
      }
      catch (ignored: InvalidPathException) {
      }
      finally {
        modifiedByUser = false
      }
    }
  }

  companion object {
    fun install(document: Document, defaultParentPath: String): FilePathDocumentChildPathHandle {
      val handle = FilePathDocumentChildPathHandle(document, Paths.get(defaultParentPath).toAbsolutePath())
      document.addDocumentListener(handle)
      return handle
    }
  }
}