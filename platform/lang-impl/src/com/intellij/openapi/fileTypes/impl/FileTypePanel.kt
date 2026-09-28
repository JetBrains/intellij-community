// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.fileTypes.FileTypesBundle
import com.intellij.openapi.fileTypes.impl.associate.OSAssociateFileTypesUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.JBSplitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.TopGap
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel

internal class FileTypePanel(recognizedFileType: JComponent, rightPanel: JComponent) {

  lateinit var myAssociateButton: JButton
  lateinit var myAssociateMessageLabel: JLabel

  @JvmField
  val panel: DialogPanel = panel {
    row {
      cell(JBSplitter(false, 0.3f))
        .align(Align.FILL)
        .applyToComponent {
          firstComponent = recognizedFileType
          secondComponent = rightPanel
        }
    }.resizableRow()
    row {
      myAssociateButton =
        button(FileTypesBundle.message("filetype.associate.button", ApplicationNamesInfo.getInstance().fullProductName)) {}
          .contextHelp(FileTypesBundle.message("filetype.associate.context.help.text", ApplicationInfo.getInstance().fullApplicationName))
          .component
      myAssociateMessageLabel = label("")
        .component
    }.topGap(TopGap.SMALL)
      .visible(OSAssociateFileTypesUtil.isAvailable())
  }
}
