// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.project.ProjectBundle
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.DslComponentProperty
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_WORD_WRAP
import com.intellij.ui.dsl.builder.VerticalComponentGap
import com.intellij.ui.dsl.builder.impl.DslComponentPropertyInternal
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.treeStructure.Tree

internal class ContentEntryTreeEditorUI(tree: Tree) {

  lateinit var excludePatternsField: JBTextField

  @JvmField
  val panel = panel {
    row {
      cell(ScrollPaneFactory.createScrollPane(tree, true))
        .applyToComponent {
          putClientProperty(DslComponentProperty.VERTICAL_COMPONENT_GAP, VerticalComponentGap(top = false))
        }
        .align(Align.FILL)
    }.resizableRow()

    val gap = IntelliJSpacingConfiguration().horizontalSmallGap
    panel {
      row(ProjectBundle.message("module.paths.exclude.patterns")) {
        excludePatternsField = textField()
          .comment(ProjectBundle.message("label.content.entry.separate.name.patterns"), maxLineLength = MAX_LINE_LENGTH_WORD_WRAP)
          .align(AlignX.FILL)
          .apply {
            comment?.putClientProperty(DslComponentPropertyInternal.PREFERRED_COLUMNS_LABEL_WORD_WRAP, 10)
          }
          .component
      }
    }.customize(UnscaledGaps(left = gap, right = gap))
  }
}