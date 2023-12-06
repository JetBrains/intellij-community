// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName(name = "TargetUIUtil")

package com.intellij.execution.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.MutableProperty
import com.intellij.ui.layout.CellBuilder
import com.intellij.ui.layout.PropertyBinding
import com.intellij.ui.layout.Row
import org.jetbrains.annotations.ApiStatus
import java.util.function.Supplier

/**
 * See [BrowsableTargetEnvironmentType.createBrowser]
 */
@ApiStatus.ScheduledForRemoval
@Deprecated("Use overloaded method with Kotlin UI DSL 2 API", level = DeprecationLevel.HIDDEN)
fun textFieldWithBrowseTargetButton(row: Row,
                                    targetType: BrowsableTargetEnvironmentType,
                                    targetSupplier: Supplier<out TargetEnvironmentConfiguration>,
                                    project: Project,
                                    @NlsContexts.DialogTitle title: String,
                                    property: PropertyBinding<String>,
                                    targetBrowserHints: TargetBrowserHints): CellBuilder<TextFieldWithBrowseButton> {
  val textFieldWithBrowseButton = TextFieldWithBrowseButton()
  val browser = targetType.createBrowser(project,
                                         title,
                                         TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                         textFieldWithBrowseButton.textField,
                                         targetSupplier,
                                         targetBrowserHints)
  textFieldWithBrowseButton.addActionListener(browser)
  textFieldWithBrowseButton.text = property.get()
  return row.component(textFieldWithBrowseButton).withBinding(TextFieldWithBrowseButton::getText,
                                                              TextFieldWithBrowseButton::setText,
                                                              property)
}

/**
 * See [BrowsableTargetEnvironmentType.createBrowser]
 */
fun com.intellij.ui.dsl.builder.Row.textFieldWithBrowseTargetButton(targetType: BrowsableTargetEnvironmentType,
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
