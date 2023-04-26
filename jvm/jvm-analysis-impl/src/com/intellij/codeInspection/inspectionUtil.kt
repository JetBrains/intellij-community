// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.codeInspection.util.InspectionMessage
import com.intellij.lang.jvm.JvmModifiersOwner
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.ChangeModifierRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.asSafely
import org.jetbrains.uast.*

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

fun ProblemsHolder.registerUProblem(element: UReferenceExpression, descriptionTemplate: @InspectionMessage String, vararg fixes: LocalQuickFix) {
  val anchor = element.referenceNameElement?.sourcePsi ?: return
  registerProblem(anchor, descriptionTemplate, *fixes)
}

fun createAddAnnotationQuickfixes(target: UDeclaration, request: AnnotationRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createAddAnnotationActions(target, request).toTypedArray(), containingFile)
}

fun createModifierQuickfixes(target: UDeclaration, request: ChangeModifierRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createModifierActions(target, request).toTypedArray(), containingFile)
}

/** When in preview this method value returns the non preview element to construct JVM actions.*/
val PsiElement.nonPreviewElement: JvmModifiersOwner? get() {
  // workaround because langElement.originalElement doesn't always work
  val physSourcePsi = PsiTreeUtil.findSameElementInCopy(navigationElement, navigationElement?.containingFile?.originalFile ?: return null)
  return physSourcePsi.toUElement()?.javaPsi?.asSafely<JvmModifiersOwner>()
}
