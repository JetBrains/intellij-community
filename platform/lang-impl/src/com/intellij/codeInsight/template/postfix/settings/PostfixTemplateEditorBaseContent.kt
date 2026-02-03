// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel

internal class PostfixTemplateEditorBaseContent(expressionTypesToolbar: ToolbarDecorator?, templateEditor: Editor) {

  lateinit var applyToTheTopmost: JBCheckBox

  @JvmField
  val panel: DialogPanel = panel {
    if (expressionTypesToolbar != null) {
      row {
        cell(expressionTypesToolbar.createPanel())
          .align(Align.FILL)
          .label(CodeInsightBundle.message("label.applicable.expression.types"), LabelPosition.TOP)
      }.resizableRow()
    }
    row {
      applyToTheTopmost = checkBox(CodeInsightBundle.message("checkbox.apply.to.the.&topmost.expression"))
        .component
    }
    row {
      cell(templateEditor.component)
        .align(Align.FILL)
        .comment(CodeInsightBundle.message("comment.use.expr.variable.to.refer.target.expression"))
    }.resizableRow()
  }
}