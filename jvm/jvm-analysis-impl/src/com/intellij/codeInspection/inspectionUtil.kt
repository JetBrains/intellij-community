// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.jvm.actions.ChangeModifierRequest
import com.intellij.lang.jvm.actions.createModifierActions
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclaration

fun ProblemsHolder.registerUProblem(element: UCallExpression, descriptionTemplate: @InspectionMessage String, vararg fixes: LocalQuickFix) {
  val anchor = element.methodIdentifier?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, *fixes)
}

fun ProblemsHolder.registerUProblem(element: UAnchorOwner, descriptionTemplate: @InspectionMessage String, vararg fixes: LocalQuickFix) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, *fixes)
}

fun ProblemsHolder.registerUProblem(element: UDeclaration, descriptionTemplate: @InspectionMessage String, vararg fixes: LocalQuickFix) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, *fixes)
}

fun createModifierQuickfixes(target: UDeclaration, request: ChangeModifierRequest): Array<LocalQuickFix>? {
  val containingFile = target.sourcePsi?.containingFile ?: return null
  return IntentionWrapper.wrapToQuickFixes(createModifierActions(target, request).toTypedArray(), containingFile)
}