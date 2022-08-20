// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.uast.UMethod
import org.jetbrains.uast.UastVisibility

class JUnitMalformedSetupTearDownInspection : AbstractBaseUastLocalInspectionTool(UMethod::class.java) {
  override fun checkMethod(method: UMethod, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    if ("setUp" != method.name && "tearDown" != method.name) return emptyArray()
    val targetClass = method.javaPsi.containingClass
    if (!InheritanceUtil.isInheritor(targetClass, JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE)) return emptyArray()
    if (!method.uastParameters.isNotEmpty() ||
        PsiType.VOID != method.returnType ||
        method.visibility != UastVisibility.PUBLIC && method.visibility != UastVisibility.PROTECTED
    ) return registerError(method, manager, isOnTheFly)
    return emptyArray()
  }

  private fun registerError(
    method: UMethod,
    manager: InspectionManager,
    isOnTheFly: Boolean
  ): Array<ProblemDescriptor> {
    val message = JvmAnalysisBundle.message("jvm.inspections.malformed.set.up.tear.down.problem.descriptor")
    val fixes = arrayOf(MakeNoArgVoidFix(method.name, false, JvmModifier.PUBLIC))
    val place = method.uastAnchor?.sourcePsi ?: return emptyArray()
    return arrayOf(manager.createProblemDescriptor(place, message, isOnTheFly, fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING))
  }
}