// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName(name = "TargetUIUtil")

package com.intellij.execution.target

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextComponentAccessor
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.IdeBorderFactory
import com.intellij.ui.layout.*
import com.intellij.util.ui.JBInsets
import java.util.function.Supplier
import javax.swing.JComponent
import javax.swing.JPanel

fun textFieldWithBrowseTargetButton(row: Row,
                                    targetType: BrowsableTargetEnvironmentType,
                                    targetSupplier: Supplier<TargetEnvironmentConfiguration>,
                                    project: Project,
                                    @NlsContexts.DialogTitle title: String,
                                    property: PropertyBinding<String>): CellBuilder<TextFieldWithBrowseButton> {
  val textFieldWithBrowseButton = TextFieldWithBrowseButton()
  val browser = targetType.createBrowser(project,
                                         title,
                                         TextComponentAccessor.TEXT_FIELD_WHOLE_TEXT,
                                         textFieldWithBrowseButton.textField,
                                         targetSupplier)
  textFieldWithBrowseButton.addActionListener(browser)
  textFieldWithBrowseButton.text = property.get()
  return row.component(textFieldWithBrowseButton).withBinding(TextFieldWithBrowseButton::getText,
                                                              TextFieldWithBrowseButton::setText,
                                                              property)
}

/**
 * Workarounds cropping the focus highlighting frame around UI components (e.g. around text fields and combo boxes) when Kotlin UI DSL
 * [panel] is placed inside arbitrary [JPanel].
 *
 * @receiver the panel where Kotlin UI DSL elements are placed
 */
fun <T : JComponent> T.fixHighlightingOfUiDslComponents(): T = apply {
  border = IdeBorderFactory.createEmptyBorder(JBInsets(4, 0, 3, 3))
}