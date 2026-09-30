// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compiler.options

import com.intellij.ide.util.ElementsChooser
import com.intellij.openapi.compiler.JavaCompilerBundle
import com.intellij.openapi.compiler.Validator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.ui.Splitter
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ValidationConfigurableUi(
  project: Project,
  validators: ElementsChooser<Validator>,
  excludedEntriesComponent: JComponent,
) {

  private val splitter: Splitter = Splitter(true).apply {
    firstComponent = panel {
      row {
        cell(validators)
          .align(Align.FILL)
          .label(JavaCompilerBundle.message("settings.validators"), LabelPosition.TOP)
          .applyToComponent { emptyText.text = JavaCompilerBundle.message("no.validators") }
      }.resizableRow()
    }
    secondComponent = panel {
      row {
        cell(excludedEntriesComponent)
          .align(Align.FILL)
          .label(JavaCompilerBundle.message("settings.exclude.from.validation"), LabelPosition.TOP)
      }.resizableRow()
    }
  }

  @JvmField
  val panel: DialogPanel = panel {
    val settings = ValidationConfiguration.getInstance(project)

    row {
      checkBox(JavaCompilerBundle.message("settings.validate.on.build"))
        .bindSelected(settings::isValidateOnBuild, settings::setValidateOnBuild)
    }.bottomGap(BottomGap.SMALL)
    row {
      cell(splitter)
        .align(Align.FILL)
    }.resizableRow()
  }
}
