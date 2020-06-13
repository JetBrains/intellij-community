// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractMethod.newImpl

import com.intellij.codeInsight.template.impl.TemplateManagerImpl
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Pair
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.refactoring.rename.inplace.InplaceRefactoring
import com.intellij.refactoring.rename.inplace.TemplateInlayUtil
import com.intellij.ui.layout.*

class InplaceMethodExtractor(val project: Project, val editor: Editor) : InplaceRefactoring(editor, null, project) {

  private val popupPanel by lazy {
    panel {
      row { this.checkBox("Make static & pass fields") }
      row { checkBox("Make constructor") }
      row { checkBox("Annotate") }
      row {
        link("Go to declaration", null) {}
        comment("Ctrl+N")
      }
      row {
        link("More options", null) { }
        comment("Ctrl+Alt+M")
      }
    }
  }


  override fun afterTemplateStart() {
    super.afterTemplateStart()
    val templateState = TemplateManagerImpl.getTemplateState(myEditor) ?: return
    val editor = templateState.editor as? EditorImpl ?: return
    val presentation = TemplateInlayUtil.createSettingsPresentation(editor)
    val offset = templateState.currentVariableRange?.endOffset ?: return
    TemplateInlayUtil.createNavigatableButtonWithPopup(templateState, offset, presentation, popupPanel) ?: return
  }

  override fun performRefactoring(): Boolean {
    return false
  }

  override fun collectAdditionalElementsToRename(stringUsages: MutableList<Pair<PsiElement, TextRange>>) {
  }

  override fun shouldSelectAll(): Boolean = false

  override fun getCommandName(): String = "TODO"

}