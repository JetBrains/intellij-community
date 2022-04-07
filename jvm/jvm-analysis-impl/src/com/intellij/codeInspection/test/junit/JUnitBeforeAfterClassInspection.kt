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
import com.intellij.psi.PsiModifier
import com.intellij.psi.PsiType
import com.siyeh.ig.junit.MakePublicStaticVoidFix
import com.siyeh.ig.psiutils.TestUtils
import org.jetbrains.uast.UMethod

class JUnitBeforeAfterClassInspection : AbstractBaseUastLocalInspectionTool() {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaMethod = method.javaPsi
    val annotation = STATIC_CONFIGS.firstOrNull {
      AnnotationUtil.isAnnotated(javaMethod, it, AnnotationUtil.CHECK_HIERARCHY)
    } ?: return emptyArray()
    val returnType = method.returnType ?: return emptyArray()
    val targetClass = javaMethod.containingClass ?: return emptyArray()

    val parameterList = method.uastParameters
    val junit4Annotation = isJunit4Annotation(annotation)
    if (junit4Annotation && (!parameterList.isEmpty() || !javaMethod.hasModifier(JvmModifier.PUBLIC)) || returnType != PsiType.VOID ||
        !javaMethod.hasModifier(JvmModifier.STATIC) && (junit4Annotation || !TestUtils.testInstancePerClass(targetClass))
    ) {
      val message = JvmAnalysisBundle.message("jvm.inspections.before.after.class.descriptor", annotation)
      val fixes = if (method.sourcePsi?.language == JavaLanguage.INSTANCE) {
        arrayOf(MakePublicStaticVoidFix(javaMethod, true, PsiModifier.PUBLIC))
      } else emptyArray()
      val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
      val problemDescriptor = manager.createProblemDescriptor(
        place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
      )
      return arrayOf(problemDescriptor)
    }
    return emptyArray()
  }

  private fun isJunit4Annotation(annotation: String) = annotation.endsWith("Class")

  companion object {
    // JUnit 4 classes
    private const val BEFORE_CLASS = "org.junit.BeforeClass"
    private const val AFTER_CLASS = "org.junit.AfterClass"

    // JUnit 5 classes
    private const val BEFORE_ALL = "org.junit.jupiter.api.BeforeAll"
    private const val AFTER_ALL = "org.junit.jupiter.api.AfterALL"

    private val STATIC_CONFIGS = arrayOf(BEFORE_CLASS, AFTER_CLASS, BEFORE_ALL, AFTER_ALL)
  }
}