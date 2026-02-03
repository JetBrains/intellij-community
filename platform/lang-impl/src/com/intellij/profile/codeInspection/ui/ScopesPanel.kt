// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.profile.codeInspection.ui

import com.intellij.codeInspection.InspectionsBundle
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

internal class ScopesPanel(private val scopesChooser: JComponent,
                           private val severityLevelChooser: JComponent,
                           private val highlightsChooser: JComponent) {

  @JvmField
  val panel = panel {
    row {
      cell(scopesChooser)
        .label(InspectionsBundle.message("inspection.scope"), LabelPosition.TOP)
        .align(AlignX.FILL)

      cell(severityLevelChooser)
        .label(InspectionsBundle.message("inspection.severity"), LabelPosition.TOP)
        .resizableColumn()
        .align(AlignX.FILL)
        .applyToComponent {
          // Don't assign the shortcut to the label, it's processed manually
          putClientProperty(DslComponentProperty.SKIP_LABEL_FOR_ASSIGNMENT, true)
        }

      cell(highlightsChooser)
        .label(InspectionsBundle.message("inspection.highlighting"), LabelPosition.TOP)
        .resizableColumn()
        .align(AlignX.FILL)
    }
  }
}