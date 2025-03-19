// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.feedback.dialog.uiBlocks

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.feedback.dialog.TEXT_FIELD_EMAIL_COLUMN_SIZE
import com.intellij.platform.feedback.dialog.feedbackAgreement
import com.intellij.platform.feedback.impl.EMAIL_REGEX
import com.intellij.platform.feedback.impl.bundle.CommonFeedbackBundle
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
  private var myCheckBoxLabel: @NlsContexts.Checkbox String = CommonFeedbackBundle.message("dialog.feedback.email.checkbox.label")
  private var myProperty: String = LicensingFacade.getInstance()?.getLicenseeEmail().orEmpty()
  private var myCheckBoxEmail: JBCheckBox? = null

  override fun addToPanel(panel: Panel) {
    panel.apply {
      row {
        checkBox(myCheckBoxLabel)
          .applyToComponent {
            myCheckBoxEmail = this
          }
      }

        indent {
          row {
            textField().bindText(::myProperty).columns(TEXT_FIELD_EMAIL_COLUMN_SIZE).applyToComponent {
              emptyText.text = CommonFeedbackBundle.message("dialog.feedback.email.textfield.placeholder")
              isEnabled = myCheckBoxEmail?.isSelected ?: false

              myCheckBoxEmail?.addActionListener { _ ->
                isEnabled = myCheckBoxEmail?.isSelected ?: false
              }
              putClientProperty(TextComponentEmptyText.STATUS_VISIBLE_FUNCTION,
                                Predicate<JBTextField> { textField -> textField.text.isEmpty() })
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.required")) {
              myCheckBoxEmail?.isSelected ?: false && it.text.isBlank()
            }.errorOnApply(CommonFeedbackBundle.message("dialog.feedback.email.textfield.invalid")) {
              myCheckBoxEmail?.isSelected ?: false && it.text.isNotBlank() && !it.text.matches(EMAIL_REGEX)
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

  fun setEmailCheckBoxLabel(@NlsContexts.Checkbox newCheckBoxLabel: String) {
    myCheckBoxLabel = newCheckBoxLabel
  }

  fun getEmailAddressIfSpecified(): String {
    if (myCheckBoxEmail?.isSelected == true) {
      return myProperty
    }
    return ""
  }
}