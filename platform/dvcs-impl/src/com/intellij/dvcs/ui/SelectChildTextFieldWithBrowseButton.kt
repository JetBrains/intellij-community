// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.DocumentAdapter
import java.nio.file.InvalidPathException
import java.nio.file.Paths
import javax.swing.event.DocumentEvent

class SelectChildTextFieldWithBrowseButton constructor(defaultParentPath: String) : TextFieldWithBrowseButton() {
  private val myDefaultParentPath = Paths.get(defaultParentPath).toAbsolutePath()
  private var myModifiedByUser = false

  init {
    text = myDefaultParentPath.toString()
    textField.document.addDocumentListener(object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        myModifiedByUser = true
      }
    })
  }

  fun trySetChildPath(child: String) {
    if (!myModifiedByUser) {
      try {
        text = myDefaultParentPath.resolve(child).toString()
      }
      catch (ignored: InvalidPathException) {
      }
      finally {
        myModifiedByUser = false
      }
    }
  }
}