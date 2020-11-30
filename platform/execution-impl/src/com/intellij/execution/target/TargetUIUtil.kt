// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName(name = "TargetUIUtil")

package com.intellij.execution.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.layout.*

class TargetUIUtil {
  companion object {
    @JvmStatic
    fun textFieldWithBrowseButton(row: Row,
                                  target: BrowsableTargetEnvironmentConfiguration,
                                  project: Project,
                                  @NlsContexts.DialogTitle title: String,
                                  property: PropertyBinding<String>): CellBuilder<TextFieldWithBrowseButton> {
      val textFieldWithBrowseButton = TextFieldWithBrowseButton()
      val browser = target.createBrowser(project,
                                         title,
                                         com.intellij.openapi.ui.TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                         textFieldWithBrowseButton.textField)
      textFieldWithBrowseButton.addActionListener(browser)
      textFieldWithBrowseButton.text = property.get()
      return row.component(textFieldWithBrowseButton).withBinding(TextFieldWithBrowseButton::getText,
                                                                  TextFieldWithBrowseButton::setText,
                                                                  property)
    }
  }
}
