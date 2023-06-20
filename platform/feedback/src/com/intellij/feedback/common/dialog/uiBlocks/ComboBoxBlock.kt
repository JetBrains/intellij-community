// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.COMBOBOX_COLUMN_SIZE
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*
import javax.swing.ListCellRenderer

class ComboBoxBlock<T>(myProperty: ObservableMutableProperty<T>,
                       @NlsContexts.Label private val myLabel: String,
                       private val myItems: List<T>,
                       @NlsContexts.DetailedDescription private val myComment: String? = null,
                       private val myRenderer: ListCellRenderer<T?>? = null,
                       private val myColumnSize: Int = COMBOBOX_COLUMN_SIZE) : SingleInputFeedbackBlock<T>(myProperty) {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        comboBox(myItems, myRenderer)
          .label(myLabel, LabelPosition.TOP)
          .bindItem(myProperty)
          .columns(myColumnSize).applyToComponent {
            selectedItem = null
          }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.combobox.required")) {
            it.selectedItem == null
          }
        if (myComment != null) {
          comment(myComment)
        }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }
}