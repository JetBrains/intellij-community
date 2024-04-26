package com.intellij.jvm.analysis.quickFix

import com.intellij.codeInspection.IntentionWrapper
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.lang.jvm.actions.AnnotationRequest
import com.intellij.lang.jvm.actions.ChangeModifierRequest
import com.intellij.lang.jvm.actions.createAddAnnotationActions
import com.intellij.lang.jvm.actions.createModifierActions
import org.jetbrains.uast.UDeclaration

fun createAddAnnotationQuickfixes(target: UDeclaration, request: AnnotationRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createAddAnnotationActions(target, request).toTypedArray(), containingFile)
}

fun createModifierQuickfixes(target: UDeclaration, request: ChangeModifierRequest): Array<LocalQuickFix> {
  val containingFile = target.sourcePsi?.containingFile ?: return emptyArray()
  return IntentionWrapper.wrapToQuickFixes(createModifierActions(target, request).toTypedArray(), containingFile)
}