// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.asSafely
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.InconvertibleTypesChecker
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.Convertible
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.LookForMutualSubclass
import com.siyeh.ig.psiutils.TypeUtils
import com.siyeh.ig.testFrameworks.UAssertHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertEqualsHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertNotEqualsHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertNotSameHint
import com.siyeh.ig.testFrameworks.UAssertHint.Companion.createAssertSameHint
import org.jetbrains.annotations.Nls
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class AssertBetweenInconvertibleTypesInspection : AbstractBaseUastLocalInspectionTool() {
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor = UastHintedVisitorAdapter.create(
    holder.file.language, AssertEqualsBetweenInconvertibleTypesVisitor(holder), arrayOf(UCallExpression::class.java), true
  )
}

private class AssertEqualsBetweenInconvertibleTypesVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    processAssertHint(createAssertEqualsHint(node), node, holder)
    processAssertHint(createAssertNotEqualsHint(node), node, holder)
    processAssertHint(createAssertSameHint(node), node, holder)
    processAssertHint(createAssertNotSameHint(node), node, holder)
    processAssertJ(node)
    return true
  }

  private fun processAssertHint(assertHint: UAssertHint?, expression: UCallExpression, holder: ProblemsHolder) {
    if (assertHint == null) return
    val firstArgument = assertHint.firstArgument
    val secondArgument = assertHint.secondArgument
    val firstParameter = expression.getParameterForArgument(firstArgument)
    if (firstParameter == null || !TypeUtils.isJavaLangObject(firstParameter.type)) return
    val secondParameter = expression.getParameterForArgument(secondArgument)
    if (secondParameter == null || !TypeUtils.isJavaLangObject(secondParameter.type)) return
    checkConvertibleTypes(expression, firstArgument, secondArgument, holder)
  }

  private fun checkConvertibleTypes(
    expression: UCallExpression,
    firstArgument: UExpression,
    secondArgument: UExpression,
    holder: ProblemsHolder
  ) {
    if (firstArgument.isNullLiteral() || secondArgument.isNullLiteral()) return
    val type1 = firstArgument.getExpressionType() ?: return
    val type2 = secondArgument.getExpressionType() ?: return
    val mismatch = InconvertibleTypesChecker.checkTypes(type1, type2, LookForMutualSubclass.IF_CHEAP)
    if (mismatch != null) {
      val name = expression.methodIdentifier?.sourcePsi ?: return
      if (mismatch.isConvertible == Convertible.CONVERTIBLE_MUTUAL_SUBCLASS_UNKNOWN) {
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

  private fun processAssertJ(call: UCallExpression) {
    if (!ASSERTJ_ASSERT_THAT_MATCHER.uCallMatches(call)) return
    val chain = call.getOutermostQualified().getQualifiedChain()
    val isEqualsCall = chain.findLast {
      it is UCallExpression && ASSERTJ_IS_EQUALS_MATCHER.uCallMatches(it)
    }.asSafely<UCallExpression>() ?: return
    val extractingCall = chain.findLast {
      it is UCallExpression && ASSERTJ_EXTRACTING_MATCHER.uCallMatches(it)
    }.asSafely<UCallExpression>()
    val sourceCall = extractingCall ?: call
    val sourceArg = sourceCall.valueArguments.firstOrNull() ?: return
    val checkArg = isEqualsCall.valueArguments.firstOrNull() ?: return
    checkConvertibleTypes(isEqualsCall, sourceArg, checkArg, holder)
  }

  fun buildErrorString(methodName: String, left: PsiType, right: PsiType): @Nls String {
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

  private fun isAssertNotEqualsMethod(methodName: String): Boolean = "assertNotEquals" == methodName || "isNotEqualTo" == methodName

  private fun isAssertNotSameMethod(methodName: String): Boolean = "assertNotSame" == methodName || "isNotSameAs" == methodName

  private companion object {
    private val ASSERTJ_IS_EQUALS_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.Assert", "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs"
    ).parameterTypes(CommonClassNames.JAVA_LANG_OBJECT)

    private val ASSERTJ_EXTRACTING_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractObjectAssert", "extracting"
    )

    private val ASSERTJ_ASSERT_THAT_MATCHER: CallMatcher = CallMatcher.staticCall(
      "org.assertj.core.api.Assertions", "assertThat"
    ).parameterCount(1)
  }
}