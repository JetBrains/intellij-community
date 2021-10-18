// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.ide.IdeDocumentationTargetProvider
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.lang.documentation.symbol.impl.symbolDocumentationTargets
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.util.PsiUtilBase

internal class DocumentationTargetsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): List<DocumentationTarget>? {
    val project = CommonDataKeys.PROJECT.getData(dataProvider) ?: return null
    val editor = CommonDataKeys.EDITOR.getData(dataProvider)
    if (editor != null) {
      fromEditor(project, editor)?.let {
        return it
      }
    }
    val symbols = CommonDataKeys.SYMBOLS.getData(dataProvider)
    if (!symbols.isNullOrEmpty()) {
      val symbolTargets = symbolDocumentationTargets(project, symbols)
      if (symbolTargets.isNotEmpty()) {
        return symbolTargets
      }
    }
    val target = CommonDataKeys.PSI_ELEMENT.getData(dataProvider)
    if (target != null) {
      return listOf(PsiElementDocumentationTarget(project, target))
    }
    return null
  }

  private fun fromEditor(project: Project, editor: Editor): List<DocumentationTarget>? {
    val file = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return null
    val ideTargetProvider = IdeDocumentationTargetProvider.getInstance(project)
    val lookup = LookupManager.getActiveLookup(editor)
    if (lookup != null) {
      val lookupElement = lookup.currentItem ?: return null
      val target = ideTargetProvider.documentationTarget(editor, file, lookupElement) ?: return null
      return listOf(target)
    }
    return ideTargetProvider.documentationTargets(editor, file, editor.caretModel.offset).takeIf {
      it.isNotEmpty()
    }
  }
}
