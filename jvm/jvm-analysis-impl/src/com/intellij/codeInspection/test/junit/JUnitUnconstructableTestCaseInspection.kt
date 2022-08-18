// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test.junit

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.*
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiTypeParameter
import com.siyeh.ig.psiutils.TestUtils
import com.siyeh.ig.psiutils.TypeUtils
import org.jetbrains.uast.UClass

class JUnitUnconstructableTestCaseInspection : AbstractBaseUastLocalInspectionTool(UClass::class.java) {
  override fun checkClass(aClass: UClass, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor> {
    val javaClass = aClass.javaPsi
    val anchor = aClass.uastAnchor?.sourcePsi ?: return emptyArray()
    if (javaClass.isInterface || javaClass.isEnum || javaClass.isAnnotationType) return emptyArray()
    if (javaClass.hasModifier(JvmModifier.ABSTRACT)) return emptyArray()
    if (javaClass is PsiTypeParameter) return emptyArray()
    if (TestUtils.isJUnitTestClass(javaClass)) { // JUnit 3
      if (!javaClass.hasModifier(JvmModifier.PUBLIC) && !aClass.isAnonymousOrLocal()) {
        val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.not.public.descriptor")
        return arrayOf(
          manager.createProblemDescriptor(
            anchor, message, isOnTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          )
        )
      }
      val constructors = javaClass.constructors.toList()
      if (constructors.isNotEmpty()) {
        val compatibleConstr = constructors.firstOrNull {
          val parameters = it.parameterList.parameters
          it.hasModifier(JvmModifier.PUBLIC)
          && (it.parameterList.isEmpty || parameters.size == 1 && TypeUtils.isJavaLangString(parameters.first().type))
        }
        if (compatibleConstr == null) {
          val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.junit3.descriptor")
          return arrayOf(manager.createProblemDescriptor(
            anchor, message, isOnTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          ))
        }
      }
    } else if (TestUtils.isJUnit4TestClass(javaClass, false)) { // JUnit 4
      if (!javaClass.hasModifier(JvmModifier.PUBLIC) && !aClass.isAnonymousOrLocal()) {
        val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.not.public.descriptor")
        return arrayOf(
          manager.createProblemDescriptor(
            anchor, message, isOnTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          )
        )
      }
      val constructors = javaClass.constructors.toList()
      if (constructors.isNotEmpty()) {
        val publicConstructors = constructors.filter { it.hasModifier(JvmModifier.PUBLIC) }
        if (publicConstructors.size != 1 || !publicConstructors.first().parameterList.isEmpty) {
          val message = JvmAnalysisBundle.message("jvm.inspections.unconstructable.test.case.junit4.descriptor")
          return arrayOf(manager.createProblemDescriptor(
            anchor, message, isOnTheFly, null, ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          ))
        }
      }
    }
    return emptyArray()
  }
}