// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.psi.PsiElement
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

class ChangeClassSignatureDialogUI(val classElement: PsiElement, val table: JComponent) {
  val panel = panel {
    row {
      text(JavaRefactoringBundle.message("changeClassSignature.class.label.text", DescriptiveNameUtil.getDescriptiveName(classElement)))
    }
    row {
      cell(table)
        .applyToComponent { minimumSize = Dimension(210, 100) }
        .align(Align.FILL)
        .label(JavaRefactoringBundle.message("changeClassSignature.parameters.panel.border.title"), LabelPosition.TOP)
    }
      .resizableRow()
  }
}