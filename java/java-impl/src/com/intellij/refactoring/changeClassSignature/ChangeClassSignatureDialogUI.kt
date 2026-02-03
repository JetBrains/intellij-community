// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.changeClassSignature

import com.intellij.java.refactoring.JavaRefactoringBundle
import com.intellij.lang.findUsages.DescriptiveNameUtil
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiElement
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import java.awt.Dimension
import javax.swing.JComponent

public class ChangeClassSignatureDialogUI(public val classElement: PsiElement, public val table: JComponent) {
  public val panel: DialogPanel = panel {
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