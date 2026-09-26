// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.actions

import com.intellij.codeInsight.intention.PriorityAction
import com.intellij.codeInspection.CommonQuickFixBundle
import com.intellij.lang.jvm.actions.CreateEnumConstantActionGroup
import com.intellij.lang.jvm.actions.CreateFieldRequest
import com.intellij.lang.jvm.actions.JvmActionGroup
import com.intellij.modcommand.ActionContext
import com.intellij.modcommand.ModPsiUpdater
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiEnumConstant
import com.intellij.psi.util.JavaElementKind
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.createSmartPointer

internal class CreateEnumConstantAction(
  target: PsiClass,
  override val request: CreateFieldRequest,
) : CreateFieldActionBase(target, request) {

  override fun getActionGroup(): JvmActionGroup = CreateEnumConstantActionGroup

  override val priority: PriorityAction.Priority get() = PriorityAction.Priority.HIGH

  override fun getText(target: PsiClass): String =
    CommonQuickFixBundle.message("fix.create.title.x", JavaElementKind.ENUM_CONSTANT.`object`(), request.fieldName)

  override fun invoke(context: ActionContext, element: PsiClass, updater: ModPsiUpdater) {
    val name = request.fieldName
    val elementFactory = JavaPsiFacade.getElementFactory(context.project)

    // add constant
    var enumConstant = elementFactory.createEnumConstantFromText(name, null)
    enumConstant = element.add(enumConstant) as PsiEnumConstant

    // start template
    val constructor = element.constructors.firstOrNull() ?: return
    val parameters = constructor.parameterList.parameters
    if (parameters.isEmpty()) return

    val paramString = parameters.joinToString(",") { it.name }
    enumConstant = enumConstant.replace(elementFactory.createEnumConstantFromText("$name($paramString)", null)) as PsiEnumConstant

    // Each argument holds the parameter name as a placeholder, and the empty template field clears it.
    val builder = updater.templateBuilder()
    val argumentList = enumConstant.argumentList ?: return
    val constantPointer = enumConstant.createSmartPointer(context.project)
    for (expression in argumentList.expressions) {
      builder.field(expression, "")
    }

    // A field write goes straight into the document, which leaves the PSI behind. So the finish offset
    // needs the committed document, or the caret stops inside the argument list.
    PsiDocumentManager.getInstance(context.project).commitDocument(updater.document)
    val liveArgumentList = constantPointer.element?.argumentList ?: return
    builder.finishAt(liveArgumentList.textRange.endOffset)
  }
}

internal fun canCreateEnumConstant(targetClass: PsiClass): Boolean {
  if (!targetClass.isEnum) return false

  val lastConstant = targetClass.fields.filterIsInstance<PsiEnumConstant>().lastOrNull()
  return lastConstant == null || !PsiTreeUtil.hasErrorElements(lastConstant)
}
