// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInsight.AnnotationUtil
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.java.JavaLanguage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiType
import com.siyeh.ig.junit.MakePublicStaticVoidFix
import org.jetbrains.uast.UMethod

class JUnitBeforeAfterInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaMethod = method.javaPsi
    val annotation = STATIC_CONFIGS.firstOrNull {
      AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY)
    } ?: return emptyArray()
    val returnType = method.returnType ?: return emptyArray()
    val parameterList = method.uastParameters
    if (parameterList.isNotEmpty() || returnType != PsiType.VOID ||
        !javaMethod.hasModifier(JvmModifier.PUBLIC) || javaMethod.hasModifier(JvmModifier.STATIC)
    ) {
      val message = JvmAnalysisBundle.message("jvm.inspections.before.after.descriptor", annotation)
      val fixes = if (method.sourcePsi?.language == JavaLanguage.INSTANCE) {
        arrayOf(MakePublicStaticVoidFix(javaMethod, false))
      } else emptyArray()
      val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
      val problemDescriptor = manager.createProblemDescriptor(
        place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      )
      return arrayOf(problemDescriptor)
    }
    return emptyArray()
  }

  companion object {
    // JUnit 4 classes
    private const val BEFORE = "org.junit.Before"
    private const val AFTER = "org.junit.After"

    private val STATIC_CONFIGS = arrayOf(BEFORE, AFTER)
  }
}