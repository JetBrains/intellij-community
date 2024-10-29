// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName(name = "TargetUIUtil")

package com.intellij.execution.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.dsl.builder.Row
import java.util.function.Supplier

/**
 * See [BrowsableTargetEnvironmentType.createBrowser]
 */
fun Row.textFieldWithBrowseTargetButton(targetType: BrowsableTargetEnvironmentType,
                                        targetSupplier: Supplier<out TargetEnvironmentConfiguration>,
                                        project: Project,
                                        @NlsContexts.DialogTitle title: String,
                                        property: MutableProperty<String>,
                                        targetBrowserHints: TargetBrowserHints = TargetBrowserHints(true)): Cell<TextFieldWithBrowseButton> {
  val textFieldWithBrowseButton = TextFieldWithBrowseButton()
  val browser = targetType.createBrowser(project,
                                         title,
                                         TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                         textFieldWithBrowseButton.textField,
                                         targetSupplier,
                                         targetBrowserHints)
  textFieldWithBrowseButton.addActionListener(browser)
  return cell(textFieldWithBrowseButton)
    .bind(TextFieldWithBrowseButton::getText, TextFieldWithBrowseButton::setText, property)
}
