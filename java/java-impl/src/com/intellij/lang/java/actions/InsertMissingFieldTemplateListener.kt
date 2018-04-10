// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.template.Template
import com.intellij.codeInsight.template.TemplateEditingAdapter
import com.intellij.codeInsight.template.impl.TemplateState
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiNameHelper
import com.intellij.psi.util.createSmartPointer

internal class InsertMissingFieldTemplateListener(
  private val project: Project,
  target: PsiClass,
  private val typeExpression: RangeExpression,
  private val isStatic: Boolean
) : TemplateEditingAdapter() {

  private val targetPointer = target.createSmartPointer(project)

  override fun beforeTemplateFinished(state: TemplateState, template: Template, brokenOff: Boolean) {
    if (brokenOff) return
    val target = targetPointer.element ?: return
    val name = state.getVariableValue(FIELD_VARIABLE)?.text ?: return
    val typeText = typeExpression.text
    CommandProcessor.getInstance().runUndoTransparentAction {
      runWriteAction {
        insertMissingField(target, name, typeText)
        PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(state.editor.document)
      }
    }
  }

  private fun insertMissingField(target: PsiClass, name: String, typeText: String) {
    if (!PsiNameHelper.getInstance(project).isIdentifier(name)) return
    if (target.findFieldByName(name, false) != null) return

    val factory = JavaPsiFacade.getElementFactory(project)
    val userType = factory.createTypeFromText(typeText, target)
    val userField = factory.createField(name, userType).setStatic(isStatic)
    target.add(userField)
  }
}
