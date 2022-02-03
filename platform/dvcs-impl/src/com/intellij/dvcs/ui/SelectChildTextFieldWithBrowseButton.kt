// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.dvcs.ui

import com.intellij.openapi.ui.TextFieldWithBrowseButton

@Deprecated("Replace with more low-level CloneDvcsDirectoryChildPathHandle")
class SelectChildTextFieldWithBrowseButton constructor(defaultParentPath: String) : TextFieldWithBrowseButton() {

  private val handle = FilePathDocumentChildPathHandle.install(textField.document, defaultParentPath)

  fun trySetChildPath(child: String) {
    handle.trySetChildPath(child)
  }
}