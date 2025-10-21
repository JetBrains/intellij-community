// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.util.InspectionMessage
import org.jetbrains.uast.*

@JvmOverloads
public fun ProblemsHolder.registerUProblem(
  element: UCallExpression,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = when (element.kind) {
    UastCallKind.METHOD_CALL -> element.methodIdentifier
    UastCallKind.CONSTRUCTOR_CALL -> element.classReference
    else -> element
  }?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
public fun ProblemsHolder.registerUProblem(
  element: UAnchorOwner,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
public fun ProblemsHolder.registerUProblem(
  element: UDeclaration,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.uastAnchor?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
public fun ProblemsHolder.registerUProblem(
  element: UReferenceExpression,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING
) {
  val anchor = element.referenceNameElement?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}

@JvmOverloads
public fun ProblemsHolder.registerUProblem(
  element: UExpression,
  descriptionTemplate: @InspectionMessage String,
  vararg fixes: LocalQuickFix,
  highlightType: ProblemHighlightType = ProblemHighlightType.GENERIC_ERROR_OR_WARNING) {
  val anchor = element.sourcePsi ?: return
  if (anchor.textLength == 0) return
  registerProblem(anchor, descriptionTemplate, highlightType, *fixes)
}