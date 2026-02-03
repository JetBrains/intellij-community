// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.completion.group.GroupedCompletionContributor
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
class PostfixTemplatesConfigurableUi {
  lateinit var completionEnabledCheckbox: JBCheckBox
  lateinit var postfixTemplatesEnabled: JBCheckBox
  lateinit var postfixTemplatesGroupCompletion: JBCheckBox
  lateinit var shortcutComboBox: ComboBox<String>
  val templatesTreeContainer: JPanel = JPanel()
  val descriptionPanel: JPanel = JPanel()

  val panel: JPanel = panel {
    row {
      postfixTemplatesEnabled = checkBox(CodeInsightBundle.message("postfix.completion.option.enabled"))
        .component
    }
    if (GroupedCompletionContributor.isGroupEnabledInApp()) {
      indent {
        row {
          postfixTemplatesGroupCompletion = checkBox(CodeInsightBundle.message("postfix.completion.option.group.enabled"))
            .component
        }
      }
    }
    else {
      //just to have non-empty
      postfixTemplatesGroupCompletion = JBCheckBox(CodeInsightBundle.message("postfix.completion.option.group.enabled"))
    }
    row {
      completionEnabledCheckbox = checkBox(CodeInsightBundle.message("postfix.completion.option.autopopup"))
        .component
    }
    row(CodeInsightBundle.message("postfix.completion.expand")) {
      shortcutComboBox = comboBox<String>(listOf())
        .component
    }
    row {
      cell(JBSplitter(false).apply {
        dividerWidth = 20
        firstComponent = templatesTreeContainer
        secondComponent = descriptionPanel
      })
        .align(Align.FILL)
    }
      .resizableRow()
  }
}