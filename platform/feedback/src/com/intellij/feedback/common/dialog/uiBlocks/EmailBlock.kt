// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.feedback.common.dialog.uiBlocks

import com.intellij.feedback.common.bundle.CommonFeedbackBundle
import com.intellij.feedback.common.dialog.EMAIL_REGEX
import com.intellij.feedback.common.dialog.TEXT_FIELD_EMAIL_COLUMN_SIZE
import com.intellij.feedback.common.feedbackAgreement
import com.intellij.openapi.project.Project
import com.intellij.ui.LicensingFacade
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.ui.components.TextComponentEmptyText
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import java.util.function.Predicate

class EmailBlock(private val myProject: Project?,
                 private val showFeedbackSystemInfoDialog: () -> Unit) : FeedbackBlock {
  private var myProperty: String = LicensingFacade.INSTANCE?.getLicenseeEmail().orEmpty()
  private var checkBoxEmail: JBCheckBox? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        checkBox(CommonFeedbackBundle.message("dialog.feedback.email.checkbox.label"))
          .applyToComponent {
            checkBoxEmail = this
          }
      }

        indent {
          row {
            textField().bindText(::myProperty).columns(TEXT_FIELD_EMAIL_COLUMN_SIZE).applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.email.textfield.placeholder")
              isEnabled = checkBoxEmail?.isSelected ?: false

              checkBoxEmail?.addActionListener { _ ->
                isEnabled = checkBoxEmail?.isSelected ?: false
              }
              putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                                Predicate<JBTextField> { textField -> textField.text.isEmpty() })
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.required")) {
              checkBoxEmail?.isSelected ?: false && it.text.isBlank()
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.invalid")) {
              checkBoxEmail?.isSelected ?: false && it.text.isNotBlank() && !it.text.matches(EMAIL_REGEX)
            }
          }.bottomGap(BottomGap.MEDIUM)
        }

      row {
        feedbackAgreement(myProject, CommonFeedbackBundle.message("dialog.feedback.consent.withEmail")) {
          showFeedbackSystemInfoDialog()
        }
      }.bottomGap(BottomGap.SMALL)
    }
  }

  fun getEmailAddressIfSpecified(): String? {
    //TODO: What if email is empty string
    if (checkBoxEmail?.isSelected == true) {
      return myProperty
    }
    return null
  }
}