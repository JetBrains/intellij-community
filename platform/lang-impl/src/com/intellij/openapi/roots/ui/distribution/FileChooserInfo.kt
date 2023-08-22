// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.ui.distribution

import com.intellij.ide.macro.Macro
import com.intellij.ide.macro.MacrosDialog
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.util.NlsContexts

interface FileChooserInfo {
  val fileChooserTitle: @NlsContexts.DialogTitle String?
  val fileChooserDescription: @NlsContexts.Label String?
  val fileChooserDescriptor: FileChooserDescriptor
  val fileChooserMacroFilter: ((Macro) -> Boolean)?

  companion object {
    val ALL: (Macro) -> Boolean = { it: Macro -> MacrosDialog.Filters.ALL.test(it) }
    val NONE: (Macro) -> Boolean = { it: Macro -> MacrosDialog.Filters.NONE.test(it) }
    val ANY_PATH: (Macro) -> Boolean = { it: Macro -> MacrosDialog.Filters.ANY_PATH.test(it) }
    val DIRECTORY_PATH: (Macro) -> Boolean = { it: Macro -> MacrosDialog.Filters.DIRECTORY_PATH.test(it) }
    val FILE_PATH: (Macro) -> Boolean = { it: Macro -> MacrosDialog.Filters.FILE_PATH.test(it) }
  }
}