// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.testFrameworks

import com.intellij.psi.*
import com.siyeh.ig.junit.JUnitCommonClassNames
import org.jetbrains.annotations.NonNls
import org.jetbrains.uast.UCallExpression
import org.jetbrains.uast.UCallableReferenceExpression
import org.jetbrains.uast.UExpression
import kotlin.math.max

private const val ORG_TESTNG_ASSERT: @NonNls String = "org.testng.Assert"

private const val ORG_TESTING_ASSERTJUNIT: @NonNls String = "org.testng.AssertJUnit"

private fun PsiClass?.parameterOrder(messageOnFirstPosition: Boolean) = when { // strictly speaking testng fail() has the message on the first position, but we ignore that here
  this != null && ORG_TESTNG_ASSERT == qualifiedName -> AbstractAssertHint.ParameterOrder.ACTUAL_EXPECTED_MESSAGE
  messageOnFirstPosition -> AbstractAssertHint.ParameterOrder.MESSAGE_EXPECTED_ACTUAL
  else -> AbstractAssertHint.ParameterOrder.EXPECTED_ACTUAL_MESSAGE
}

private fun PsiMethod.isMessageOnFirstPosition(): Boolean {
  val containingClass = containingClass ?: return false
  val qualifiedName = containingClass.qualifiedName
  return ORG_TESTING_ASSERTJUNIT == qualifiedName || ORG_TESTNG_ASSERT == qualifiedName && "fail" == name || JUnitCommonClassNames.JUNIT_FRAMEWORK_ASSERT == qualifiedName || JUnitCommonClassNames.ORG_JUNIT_ASSERT == qualifiedName || JUnitCommonClassNames.JUNIT_FRAMEWORK_TEST_CASE == qualifiedName || JUnitCommonClassNames.ORG_JUNIT_ASSUME == qualifiedName
}

private fun PsiMethod.isMessageOnLastPosition(): Boolean {
  val containingClass = containingClass ?: return false
  val qualifiedName = containingClass.qualifiedName
  return ORG_TESTNG_ASSERT == qualifiedName && "fail" != name || JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSERTIONS == qualifiedName || JUnitCommonClassNames.ORG_JUNIT_JUPITER_API_ASSUMPTIONS == qualifiedName
}

/** Returns whether a given parameter type looks like an assertion message. */
private fun PsiParameter.isAssertionMessage(): Boolean {
  val type = type
  return type.equalsToText(CommonClassNames.JAVA_LANG_STRING) || type.equalsToText(
    CommonClassNames.JAVA_UTIL_FUNCTION_SUPPLIER + "<" + CommonClassNames.JAVA_LANG_STRING + ">")
}

public abstract class AbstractAssertHint<E> {
  public enum class ParameterOrder {
    /** JUnit 3/JUnit 4 */
    MESSAGE_EXPECTED_ACTUAL,

    /** JUnit 5 */
    EXPECTED_ACTUAL_MESSAGE,

    /** Testng */
    ACTUAL_EXPECTED_MESSAGE
  }

  public abstract val parameterOrder: ParameterOrder

  public abstract val method: PsiMethod

  /** Expression this [AbstractAssertHint] was created from. */
  public abstract val originalExpression: E

  /** Message argument */
  public abstract val message: E?

  /** First argument index, excluding [message]. */
  public abstract val argIndex: Int

  /** First argument, excluding [message]. */
  public abstract val firstArgument: E

  /** Second argument, excluding [message]. */
  public abstract val secondArgument: E

  public val isAssertTrue: Boolean get() = "assertTrue" == method.name

  public val expected: E get() = if (parameterOrder != ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  public val actual: E get() = if (parameterOrder == ParameterOrder.ACTUAL_EXPECTED_MESSAGE) firstArgument else secondArgument

  public val isMessageOnFirstPosition: Boolean get() = parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  public val isExpectedActualOrder: Boolean
    get() = parameterOrder == ParameterOrder.EXPECTED_ACTUAL_MESSAGE || parameterOrder == ParameterOrder.MESSAGE_EXPECTED_ACTUAL

  /** Get actual argument from expected argument and vice versa. */
  public fun getOtherExpression(expression: E): E? = when (expression) {
    firstArgument -> secondArgument
    secondArgument -> firstArgument
    else -> null
  }


  public companion object {
    @JvmField
    public val ASSERT_METHOD_2_PARAMETER_COUNT: @NonNls Map<String, Int> = mapOf("assertArrayEquals" to 2, "assertEquals" to 2,
                                                                                 "assertNotEquals" to 2, "assertFalse" to 1, "assumeFalse" to 1,
                                                                                 "assertNotNull" to 1, "assertNotSame" to 2, "assertNull" to 1,
                                                                                 "assertSame" to 2, "assertThat" to 2, "assertTrue" to 1,
                                                                                 "assumeTrue" to 1, "fail" to 0,
                                                                                 "assertEqualsNoOrder" to 2 //testng
    )
  }
}

private fun <E> getMessage(parameters: Array<PsiParameter>,
                           arguments: Array<E>,
                           messageOnFirstPosition: Boolean,
                           minimumParamCount: Int): Pair<Int, E>? = if (messageOnFirstPosition) {
  if (parameters.isNotEmpty()
      && parameters.first().type.equalsToText(CommonClassNames.JAVA_LANG_STRING)
      && parameters.size > minimumParamCount
  ) 1 to arguments.first()
  else null
}
else {
  if (parameters.size > minimumParamCount && minimumParamCount >= 0) {
    val lastParameterIdx = parameters.size - 1 //check that it's not delta in assertEquals(dbl, dbl, dbl), etc
    if (parameters[lastParameterIdx].type is PsiClassType) {
      0 to arguments[lastParameterIdx]
    }
    else null
  }
  else null
}

public class AssertHint private constructor(override val argIndex: Int,
                                            override val parameterOrder: ParameterOrder,
                                            override val message: PsiExpression?,
                                            override val method: PsiMethod,
                                            override val originalExpression: PsiExpression) : AbstractAssertHint<PsiExpression>() {
  override val firstArgument: PsiExpression get() = getArgument(argIndex)

  override val secondArgument: PsiExpression get() = getArgument(argIndex + 1)

  private fun getArgument(index: Int): PsiExpression = (originalExpression as PsiMethodCallExpression).argumentList.expressions[index] as PsiExpression

  public companion object {
    @JvmStatic
    public fun createAssertEqualsHint(expression: PsiMethodCallExpression): AssertHint? = create(expression) { methodName ->
      if ("assertEquals" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertNotEqualsHint(expression: PsiMethodCallExpression): AssertHint? = create(expression) { methodName ->
      if ("assertNotEquals" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertTrueFalseHint(expression: PsiMethodCallExpression): AssertHint? = create(expression) { methodName ->
      if ("assertTrue" == methodName || "assertFalse" == methodName) 1 else null
    }

    @JvmStatic
    public fun createAssertSameHint(expression: PsiMethodCallExpression): AssertHint? = create(expression) { methodName ->
      if ("assertSame" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertNotSameHint(expression: PsiMethodCallExpression): AssertHint? = create(expression) { methodName ->
      if ("assertNotSame" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertEqualsLikeHintForCompletion(call: PsiMethodCallExpression?,
                                                       args: Array<PsiExpression?>,
                                                       method: PsiMethod,
                                                       index: Int): AssertHint? {
      if (call == null) return null
      val name = method.name
      if (args.isEmpty()) return null
      val argCount = max(index + 1, args.size)
      if (argCount != 2 && argCount != 3) return null
      if ("assertEquals" != name && "assertNotEquals" != name && "assertSame" != name && "assertNotSame" != name) return null
      val parameters = method.parameterList.parameters
      if (argCount != parameters.size) return null
      if (argCount == 2) {
        return AssertHint(0, method.containingClass.parameterOrder(false), null, method, call)
      }
      if (parameters.first().isAssertionMessage() && args.size > 1) {
        return AssertHint(1, method.containingClass.parameterOrder(true), args.first(), method, call)
      }
      if (parameters[2].isAssertionMessage() && args.size > 2) {
        return AssertHint(0, method.containingClass.parameterOrder(false), args[2], method, call)
      }
      return null
    }

    @JvmStatic
    public fun create(expression: PsiMethodCallExpression, methodNameToParamCount: (String) -> Int?): AssertHint? {
      val methodExpression = expression.methodExpression
      val methodName = methodExpression.referenceName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val resolveResult = expression.resolveMethodGenerics()
      val method = resolveResult.element as PsiMethod?
      if (method == null || method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult) return null
      val messageOnLastPosition = method.isMessageOnLastPosition()
      val messageOnFirstPosition = method.isMessageOnFirstPosition()
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameterList = method.parameterList
      val parameters = parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      val arguments = expression.argumentList.expressions
      val (argumentIndex, message) = getMessage(parameters, arguments, messageOnFirstPosition, minimumParamCount) ?: (0 to null)
      return AssertHint(argumentIndex, method.containingClass.parameterOrder(messageOnFirstPosition), message, method, expression)
    }

    @JvmStatic
    public fun create(methodExpression: PsiMethodReferenceExpression, methodNameToParamCount: (String) -> Int?): AssertHint? {
      val methodName = methodExpression.referenceName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val resolveResult = methodExpression.advancedResolve(false)
      val method = resolveResult.element as? PsiMethod ?: return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE) || !resolveResult.isValidResult) return null
      val messageOnLastPosition = method.isMessageOnLastPosition()
      val messageOnFirstPosition = method.isMessageOnFirstPosition()
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameters = method.parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      return AssertHint(0, method.containingClass.parameterOrder(messageOnFirstPosition), null, method, methodExpression)
    }
  }
}

public class UAssertHint private constructor(override val argIndex: Int,
                                             override val parameterOrder: ParameterOrder,
                                             override val message: UExpression?,
                                             override val method: PsiMethod,
                                             override val originalExpression: UExpression) : AbstractAssertHint<UExpression>() {
  override val firstArgument: UExpression get() = getArgument(argIndex)

  override val secondArgument: UExpression get() = getArgument(argIndex + 1)

  private fun getArgument(index: Int): UExpression = (originalExpression as UCallExpression).valueArguments[index]

  public companion object {
    @JvmStatic
    public fun createAssertEqualsHint(expression: UCallExpression): UAssertHint? = create(expression) { methodName ->
      if ("assertEquals" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertNotEqualsHint(expression: UCallExpression): UAssertHint? = create(expression) { methodName ->
      if ("assertNotEquals" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertSameHint(expression: UCallExpression): UAssertHint? = create(expression) { methodName ->
      if ("assertSame" == methodName) 2 else null
    }

    @JvmStatic
    public fun createAssertNotSameHint(expression: UCallExpression): UAssertHint? = create(expression) { methodName ->
      if ("assertNotSame" == methodName) 2 else null
    }

    @JvmStatic
    public fun create(expression: UCallExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = expression.methodName ?: return null
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = expression.resolve() ?: return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = method.isMessageOnFirstPosition()
      val messageOnLastPosition = method.isMessageOnLastPosition()
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameters = method.parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      val arguments = expression.valueArguments.toTypedArray()
      val (argumentIndex, message) = getMessage(parameters, arguments, messageOnFirstPosition, minimumParamCount) ?: (0 to null)
      return UAssertHint(argumentIndex, method.containingClass.parameterOrder(messageOnFirstPosition), message, method, expression)
    }

    @JvmStatic
    public fun create(refExpression: UCallableReferenceExpression, methodNameToParamCount: (String) -> Int?): UAssertHint? {
      val methodName = refExpression.callableName
      val minimumParamCount = methodNameToParamCount(methodName) ?: return null
      val method = refExpression.resolve() ?: return null
      if (method !is PsiMethod) return null
      if (method.hasModifierProperty(PsiModifier.PRIVATE)) return null
      val messageOnFirstPosition = method.isMessageOnFirstPosition()
      val messageOnLastPosition = method.isMessageOnLastPosition()
      if (!messageOnFirstPosition && !messageOnLastPosition) return null
      val parameters = method.parameterList.parameters
      if (parameters.size < minimumParamCount) return null
      return UAssertHint(0, method.containingClass.parameterOrder(messageOnFirstPosition), null, method, refExpression)
    }
  }
}
