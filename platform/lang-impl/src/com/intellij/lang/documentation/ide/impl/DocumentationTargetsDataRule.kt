// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.documentation.ide.impl

import com.intellij.ide.impl.dataRules.GetDataRule
import com.intellij.lang.documentation.DocumentationTarget
import com.intellij.lang.documentation.psi.PsiElementDocumentationTarget
import com.intellij.lang.documentation.psi.psiDocumentationTarget
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.psi.util.PsiUtilBase

class DocumentationTargetsDataRule : GetDataRule {

  override fun getData(dataProvider: DataProvider): List<DocumentationTarget>? {
    val project = CommonDataKeys.PROJECT.getData(dataProvider) ?: return null
    val editor = CommonDataKeys.EDITOR.getData(dataProvider)
    if (editor != null) {
      val file = PsiUtilBase.getPsiFileInEditor(editor, project) ?: return null
      val target = psiDocumentationTarget(project, editor, file, editor.caretModel.offset)
      if (target != null) {
        return listOf(target)
      }
    }
    val target = CommonDataKeys.PSI_ELEMENT.getData(dataProvider)
    if (target != null) {
      return listOf(PsiElementDocumentationTarget(project, target, sourceElement = null, anchor = null))
    }
    return null
  }
}
