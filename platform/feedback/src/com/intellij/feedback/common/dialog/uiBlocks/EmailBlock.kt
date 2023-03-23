// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.CommonFeedbackSystemInfoData
import com.intellij.feedback.common.dialog.EMAIL_REGEX
import com.intellij.feedback.common.dialog.TEXT_FIELD_EMAIL_COLUMN_SIZE
import com.intellij.feedback.common.dialog.showFeedbackSystemInfoDialog
import com.intellij.feedback.common.feedbackAgreement
import com.intellij.openapi.observable.properties.ObservableMutableProperty
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.*
import java.util.function.Predicate

class EmailBlock(property: ObservableMutableProperty<String>,
                 val myProject: Project?,
                 private val mySystemInfoData: CommonFeedbackSystemInfoData) : SingleInputFeedbackBlock<String>(property) {

  private var checkBoxEmailProperty: Boolean = false
  private var checkBoxEmail: JBCheckBox? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      panel {
        row {
          checkBox(CommonFeedbackBundle.message("dialog.feedback.email.checkbox.label"))
            .bindSelected(::checkBoxEmailProperty)
            .applyToComponent {
              checkBoxEmail = this
            }
        }.topGap(TopGap.MEDIUM)

        indent {
          row {
            textField().bindText(myProperty).columns(TEXT_FIELD_EMAIL_COLUMN_SIZE).applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.email.textfield.placeholder")
              isEnabled = checkBoxEmailProperty

              checkBoxEmail?.addActionListener { _ ->
                isEnabled = checkBoxEmailProperty
              }
              putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                                Predicate<JBTextField> { textField -> textField.text.isEmpty() })
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.required")) {
              checkBoxEmailProperty && it.text.isBlank()
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.invalid")) {
              checkBoxEmailProperty && it.text.isNotBlank() && !it.text.matches(EMAIL_REGEX)
            }
          }.bottomGap(BottomGap.MEDIUM)
        }

        row {
          feedbackAgreement(myProject, CommonFeedbackBundle.message("dialog.feedback.consent.withEmail")) {
            showFeedbackSystemInfoDialog(myProject, mySystemInfoData)
          }
        }.bottomGap(BottomGap.SMALL).topGap(TopGap.MEDIUM)
      }
    }
  }

  override fun collectInput(): String {
    return myProperty.get()
  }

}