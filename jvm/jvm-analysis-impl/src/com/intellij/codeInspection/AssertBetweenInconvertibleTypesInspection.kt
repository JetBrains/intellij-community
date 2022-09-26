// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.InconvertibleTypesChecker
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.Convertible
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.LookForMutualSubclass
import com.siyeh.ig.psiutils.TypeUtils
import com.siyeh.ig.testFrameworks.UAssertHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertEqualsUHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertNotEqualsUHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertNotSameUHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertSameUHint
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.NotNull
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class AssertBetweenInconvertibleTypesInspection : AbstractBaseUastLocalInspectionTool() {

  private val ASSERTJ_IS_EQUAL: CallMatcher = CallMatcher.instanceCall(
    "org.assertj.core.api.Assert", "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs")
    .parameterTypes(CommonClassNames.JAVA_LANG_OBJECT)
  private val ASSERTJ_DESCRIBED: CallMatcher = CallMatcher.instanceCall(
    "org.assertj.core.api.Descriptable", "describedAs", "as")
  private val ASSERTJ_ASSERT_THAT: CallMatcher = CallMatcher.staticCall(
    "org.assertj.core.api.Assertions", "assertThat").parameterCount(1)

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, AssertEqualsBetweenInconvertibleTypesVisitor(holder), arrayOf(UCallExpression::class.java), true)

  override fun getID(): String = "AssertBetweenInconvertibleTypes"

  inner class AssertEqualsBetweenInconvertibleTypesVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitCallExpression(node: UCallExpression): Boolean {
      processAssertHint(createAssertEqualsUHint(node), node, holder)
      processAssertHint(createAssertNotEqualsUHint(node), node, holder)
      processAssertHint(createAssertSameUHint(node), node, holder)
      processAssertHint(createAssertNotSameUHint(node), node, holder)
      processAssertJ(node)
      return true
    }

    private fun processAssertHint(assertHint: UAssertHint?, expression: UCallExpression, holder : ProblemsHolder) {
      if (assertHint == null) return
      val firstArgument: UExpression
      val secondArgument: UExpression
      try {
        firstArgument = assertHint.firstArgument
        secondArgument = assertHint.secondArgument
      }
      catch (ignored: Exception) {
        return
      }
      val firstParameter = expression.getParameterForArgument(firstArgument)
      if (firstParameter == null || !TypeUtils.isJavaLangObject(firstParameter.type)) return
      val secondParameter = expression.getParameterForArgument(secondArgument)
      if (secondParameter == null || !TypeUtils.isJavaLangObject(secondParameter.type)) return
      checkConvertibleTypes(expression, firstArgument, secondArgument, holder)
    }
    private fun checkConvertibleTypes(expression: UCallExpression, firstArgument: UExpression, secondArgument: UExpression,
                                      holder : ProblemsHolder) {
      if (firstArgument.isNullLiteral() || secondArgument.isNullLiteral()) return
      val type1 = firstArgument.getExpressionType() ?: return
      val type2 = secondArgument.getExpressionType() ?: return
      val mismatch = InconvertibleTypesChecker.checkTypes(type1, type2, LookForMutualSubclass.IF_CHEAP)
      if (mismatch != null) {
        val name = expression.methodIdentifier?.sourcePsi ?: return
        val convertible = mismatch.isConvertible
        if (convertible == Convertible.CONVERTIBLE_MUTUAL_SUBCLASS_UNKNOWN) {
          holder.registerPossibleProblem(name)
        }
        else {
          val methodName = expression.methodName ?: return
          val highlightType = if (isAssertNotEqualsMethod(methodName)) ProblemHighlightType.WEAK_WARNING
          else ProblemHighlightType.GENERIC_ERROR_OR_WARNING
          val errorString = buildErrorString(methodName, mismatch.left, mismatch.right)
          holder.registerProblem(name, errorString, highlightType)
        }
      }
    }
    private fun getQualifier(call: UCallExpression) : UCallExpression? {
      val receiver = call.getParentOfType<UQualifiedReferenceExpression>()?.receiver
      if (receiver is UCallExpression) return receiver
      if (receiver is UQualifiedReferenceExpression) {
        val selector = receiver.selector
        if (selector is UCallExpression) return selector
      }
      return null
    }

    private fun processAssertJ(call: UCallExpression) {
      if (!ASSERTJ_IS_EQUAL.uCallMatches(call)) return
      var qualifier = getQualifier(call)
      if (qualifier == null) return
      val chain = qualifier.getOutermostQualified().getQualifiedChain()

      val lastDescribed = chain.lastOrNull { expr ->
        expr is UCallExpression && ASSERTJ_DESCRIBED.uCallMatches(expr)
      }
      if (lastDescribed != null) {
        qualifier = getQualifier(lastDescribed as UCallExpression)
      }
      if (qualifier == null || !ASSERTJ_ASSERT_THAT.uCallMatches(qualifier)) return
      val callValueArguments = call.valueArguments
      val qualValueArguments = qualifier.valueArguments
      if (callValueArguments.isEmpty() || qualValueArguments.isEmpty()) return
      checkConvertibleTypes(call, callValueArguments[0], qualValueArguments[0], holder)
    }
  }
  @Nls
  fun buildErrorString(methodName: @NotNull String, left: @NotNull PsiType, right: @NotNull PsiType): String {
    val comparedTypeText = left.presentableText
    val comparisonTypeText = right.presentableText
    if (isAssertNotEqualsMethod(methodName)) {
      return JvmAnalysisBundle.message("jvm.inspections.assertnotequals.between.inconvertible.types.problem.descriptor", comparedTypeText,
                                             comparisonTypeText)
    }
    return if (isAssertNotSameMethod(methodName)) {
      JvmAnalysisBundle.message("jvm.inspections.assertnotsame.between.inconvertible.types.problem.descriptor", comparedTypeText, comparisonTypeText)
    }
    else JvmAnalysisBundle.message("jvm.inspections.assertequals.between.inconvertible.types.problem.descriptor",
                                         StringUtil.escapeXmlEntities(comparedTypeText),
                                         StringUtil.escapeXmlEntities(comparisonTypeText))
  }

  private fun isAssertNotEqualsMethod(methodName: @NonNls String): Boolean {
    return "assertNotEquals".equals(methodName) || "isNotEqualTo".equals(methodName)
  }

  private fun isAssertNotSameMethod(methodName: @NonNls String): Boolean {
    return "assertNotSame".equals(methodName) || "isNotSameAs".equals(methodName)
  }
}

