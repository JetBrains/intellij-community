// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection

import com.intellij.psi.*
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UExpression

class UAssertHint private constructor(
  val argIndex: Int,
  val parameterOrder: ParameterOrder,
  val message: UExpression?,
  val method: PsiMethod,
  val originalExpression: UExpression
) {
  enum class ParameterOrder {
    /** Junit 3/Junit 4 */
    MESSAGE_EXPECTED_ACTUAL,

    /** Junit 5 */
    EXPECTED_ACTUAL_MESSAGE,

    /** Testng */
    ACTUAL_EXPECTED_MESSAGE
  }

  val firstArgument: UExpression? get() = (originalExpression as? UCallExpression)?.valueArguments?.get(argIndex)

  val secondArgument: UExpression? get() = (originalExpression as? UCallExpression)?.valueArguments?.get(argIndex + 1)

  val expected: UExpression? get() = if (parameterOrder != ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  val actual: UExpression? get() = if (parameterOrder == ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  val isMessageOnFirstPosition: Boolean get() = parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  val isExpectedActualOrder: Boolean
    get() = parameterOrder == ParameterOrder.EXPECTED_ACTUAL_MESSAGE ||
            parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  companion object {
    private const val ORG_TESTNG_ASSERT: @NonNls String = "org.testng.Assert"
    private const val ORG_TESTING_ASSERTJUNIT: @NonNls String = "org.testng.AssertJUnit"

    @JvmStatic
    fun create(expression: UCallExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = expression.methodName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = expression.resolve() ?: return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = isMessageOnFirstPosition(method)
      val messageOnLastPosition = isMessageOnLastPosition(method)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameters = method.parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      val arguments = expression.valueArguments
      val argumentIndex: Int
      val message = if (messageOnFirstPosition) {
        if (parameters.isNotEmpty() &&
            parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_STRING) &&
            parameters.size > minimumParamCount
        ) {
          argumentIndex = 1
          arguments.first()
        }
        else {
          argumentIndex = 0
          null
        }
      }
      else {
        argumentIndex = 0
        if (parameters.size > minimumParamCount && minimumParamCount >= 0) {
          val lastParameterIdx = parameters.size - 1
          //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
          if (parameters[lastParameterIdx].type is PsiClassType) {
            arguments[lastParameterIdx]
          }
          else null
        }
        else null
      }
      val containingClass = method.containingClass
      return UAssertHint(argumentIndex, parameterOrder(containingClass, messageOnFirstPosition), message, method, expression)
    }

    fun create(refExpression: UCallableReferenceExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = refExpression.callableName
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = refExpression.resolve() ?: return null
      if (method !is PsiMethod) return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = isMessageOnFirstPosition(method)
      val messageOnLastPosition = isMessageOnLastPosition(method)
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameterList = method.parameterList
      val parameters = parameterList.parameters
      if (parameters.size != minimumParamCount) return null
      val containingClass = method.containingClass
      return UAssertHint(0, parameterOrder(containingClass, messageOnFirstPosition), null, method, refExpression)
    }

    private fun parameterOrder(containingClass: PsiClass?, messageOnFirstPosition: Boolean): ParameterOrder = when {
      // strictly speaking testng fail() has the message on the first position, but we ignore that here
      containingClass != null && ORG_TESTNG_ASSERT == containingClass.qualifiedName -> ParameterOrder.ACTUAL_EXPECTED_MESSAGE
      messageOnFirstPosition -> ParameterOrder.MESSAGE_EXPECTED_ACTUAL
      else -> ParameterOrder.EXPECTED_ACTUAL_MESSAGE
    }

    private fun isMessageOnFirstPosition(method: PsiMethod): Boolean {
      val containingClass = method.containingClass ?: return false
      val qualifiedName = containingClass.qualifiedName
      return ORG_TESTING_ASSERTJUNIT == qualifiedName ||
             ORG_TESTNG_ASSERT == qualifiedName && "fail" == method.name ||
             JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT == qualifiedName ||
             JUnitCommonClassNames.ORG_JUNIT_ASSERT == qualifiedName ||
             JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE == qualifiedName ||
             JUnitCommonClassNames.ORG_JUNIT_ASSUME == qualifiedName
    }

    private fun isMessageOnLastPosition(method: PsiMethod): Boolean {
      val containingClass = method.containingClass ?: return false
      val qualifiedName = containingClass.qualifiedName
      return ORG_TESTNG_ASSERT == qualifiedName && "fail" != method.name ||
             JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS == qualifiedName ||
             JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS == qualifiedName
    }
  }
}