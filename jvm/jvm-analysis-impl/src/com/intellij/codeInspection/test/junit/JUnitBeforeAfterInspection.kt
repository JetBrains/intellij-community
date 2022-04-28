// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.lang.jvm.actions.createModifierActions
import com.intellij.lang.jvm.actions.modifierRequest
import com.intellij.psi.PsiModifier.ModifierConstant
import com.intellij.psi.PsiType
import org.jetbrains.uast.UMethod

class JUnitBeforeAfterInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaMethod = method.javaPsi
    val annotation = ANNOTATIONS.firstOrNull {
      AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY)
    } ?: return emptyArray()
    val returnType = method.returnType ?: return emptyArray()
    val parameterList = method.uastParameters
    if ((parameterList.isNotEmpty() || returnType != PsiType.VOID || javaMethod.hasModifier(JvmModifier.STATIC))
        || (isJUnit4(annotation) && !javaMethod.hasModifier(JvmModifier.PUBLIC))
    ) return makeStaticVoidFix(method, manager, isOnTheFly, annotation, if (isJUnit4(annotation)) JvmModifier.PUBLIC else null)
    if (isJUnit5(annotation) && javaMethod.hasModifier(JvmModifier.PRIVATE)) {
      return removePrivateModifierFix(method, manager, isOnTheFly, annotation)
    }
    return emptyArray()
  }

  private fun makeStaticVoidFix(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    annotation: String,
    @ModifierConstant modifier: JvmModifier? // null means unchanged
  ): Array<ProblemDescriptor> {
    val message = JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation)
    val fixes = arrayOf(MakeNoArgVoidFix(method.name, false, modifier))
    val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
    val problemDescriptor = manager.createProblemDescriptor(
      place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
    return arrayOf(problemDescriptor)
  }

  private fun removePrivateModifierFix(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean,
    annotation: String,
  ): Array<ProblemDescriptor> {
    val message = JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation)
    val containingFile = method.sourcePsi?.containingFile ?: return emptyArray()
    val fixes = IntentionWrapper.wrapToQuickFixes(
      createModifierActions(method, modifierRequest(JvmModifier.PRIVATE, false)), containingFile
    ).toTypedArray()
    val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
    val problemDescriptor = manager.createProblemDescriptor(
      place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
    )
    return arrayOf(problemDescriptor)
  }

  private fun isJUnit4(annotation: String) = annotation.endsWith(BEFORE) || annotation.endsWith(AFTER)

  private fun isJUnit5(annotation: String) = annotation.endsWith(BEFORE_EACH) || annotation.endsWith(AFTER_EACH)

  companion object {
    // JUnit 4 classes
    private const val BEFORE = "org.junit.Before"
    private const val AFTER = "org.junit.After"

    // JUnit 5 classes
    private const val BEFORE_EACH = "org.junit.jupiter.api.BeforeEach"
    private const val AFTER_EACH = "org.junit.jupiter.api.AfterEach"

    private val ANNOTATIONS = arrayOf(BEFORE, AFTER, BEFORE_EACH, AFTER_EACH)
  }
}