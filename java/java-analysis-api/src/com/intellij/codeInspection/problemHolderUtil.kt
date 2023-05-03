// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.uast.UAnchorOwner
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UDeclaration
import org.jetbrains.uast.UReferenceExpression

@JvmOverloads
fun ProblemsHolder.registerUProblem(
  element: UCallExpression,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.methodIdentifier?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
fun ProblemsHolder.registerUProblem(
  element: UAnchorOwner,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
fun ProblemsHolder.registerUProblem(
  element: UDeclaration,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
fun ProblemsHolder.registerUProblem(
  element: UReferenceExpression,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.referenceNameElement?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}