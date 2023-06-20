// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.*

class CheckBoxGroupBlock(
  @NlsContexts.Label private val myGroupLabel: String,
  private val myCheckBoxProperties: List<ObservableMutableProperty<Boolean>>,
  private val myCheckBoxLabels: List<String>,
  private val myOtherProperty: ObservableMutableProperty<String>? = null
) : BaseFeedbackBlock() {
  override fun addToPanel(panel: Panel) {
    panel.apply {
      @Suppress("DialogTitleCapitalization")
      buttonsGroup(myGroupLabel) {
        for (i: Int in myCheckBoxProperties.indices) {
          row {
            checkBox(myCheckBoxLabels[i]).bindSelected(myCheckBoxProperties[i])
          }.apply {
            if (i == myCheckBoxProperties.size - 1 && myOtherProperty == null) {
              this.bottomGap(BottomGap.MEDIUM)
            }
          }
        }

        if (myOtherProperty != null) {
          row {
            textField().applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.checkboxGroup.other.placeholder")
            }.bindText(myOtherProperty).columns(COLUMNS_MEDIUM)
          }.bottomGap(BottomGap.MEDIUM)
        }
      }
    }
  }
}