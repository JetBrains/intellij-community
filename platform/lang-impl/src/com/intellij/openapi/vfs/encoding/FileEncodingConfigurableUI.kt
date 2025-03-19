// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.vfs.encoding.EncodingProjectManagerImpl.BOMForNewUTF8Files
import com.intellij.ui.EnumComboBoxModel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import org.jetbrains.annotations.ApiStatus
import java.awt.event.ItemListener
import javax.swing.JComponent

@ApiStatus.Internal
class FileEncodingConfigurableUI {

  lateinit var transparentNativeToAsciiCheckBox: JBCheckBox
  lateinit var bomForUTF8Combo: ComboBox<BOMForNewUTF8Files>

  private lateinit var bomForUTF8ComboCell: Cell<ComboBox<BOMForNewUTF8Files>>

  fun createContent(tablePanel: JComponent, filesEncodingCombo: JComponent): DialogPanel {
    return panel {
      row {
        cell(tablePanel).align(Align.FILL)
      }.resizableRow()
        .bottomGap(BottomGap.SMALL)

      row(IdeBundle.message("editbox.default.encoding.for.properties.files")) {
        cell(filesEncodingCombo)
      }

      indent {
        row {
          transparentNativeToAsciiCheckBox = checkBox(IdeBundle.message("checkbox.transparent.native.to.ascii.conversion")).component
        }.bottomGap(BottomGap.SMALL)
      }

      row(IdeBundle.message("file.encoding.option.create.utf8.files")) {
        bomForUTF8ComboCell = comboBox(EnumComboBoxModel(BOMForNewUTF8Files::class.java)).applyToComponent {
          addItemListener(ItemListener { updateExplanationLabelText() })
        }.comment("")
        bomForUTF8Combo = bomForUTF8ComboCell.component
      }.layout(RowLayout.INDEPENDENT)
    }
  }

  private fun updateExplanationLabelText() {
    val item = bomForUTF8ComboCell.component.selectedItem as BOMForNewUTF8Files
    val productName = ApplicationNamesInfo.getInstance().productName
    val comment = when (item) {
      BOMForNewUTF8Files.ALWAYS -> IdeBundle.message("file.encoding.option.warning.always", productName)
      BOMForNewUTF8Files.NEVER -> IdeBundle.message("file.encoding.option.warning.never", productName)
      BOMForNewUTF8Files.WINDOWS_ONLY -> IdeBundle.message("file.encoding.option.warning.windows.only", productName)
    }
    bomForUTF8ComboCell.comment?.text = comment
  }
}