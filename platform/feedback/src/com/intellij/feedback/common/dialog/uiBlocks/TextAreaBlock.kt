// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.dialog.TEXT_AREA_COLUMN_SIZE
import com.intellij.feedback.common.dialog.TEXT_AREA_ROW_SIZE
import com.intellij.feedback.common.dialog.adjustBehaviourForFeedbackForm
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class TextAreaBlock(myProperty: ObservableMutableProperty<String>,
                    @NlsContexts.Label val myLabel: String,
                    private val myTextAreaRowSize: Int = TEXT_AREA_ROW_SIZE,
                    private val myTextAreaColumnSize: Int = TEXT_AREA_COLUMN_SIZE,
                    @NlsContexts.DialogMessage private val myShouldNotEmptyMessage: String? = null
) : SingleInputFeedbackBlock<String>(myProperty) {

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        textArea()
          .bindText(myProperty)
          .rows(myTextAreaRowSize)
          .columns(myTextAreaColumnSize)
          .label(myLabel, LabelPosition.TOP)
          .applyToComponent {
            adjustBehaviourForFeedbackForm()
          }
          .apply {
            if (myShouldNotEmptyMessage != null) {
              errorOnApply(myShouldNotEmptyMessage) {
                myProperty.get().isBlank()
              }
            }
          }
      }.bottomGap(BottomGap.MEDIUM)
    }
  }

  override fun collectInput(): String {
    return myProperty.get()
  }
}