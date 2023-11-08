// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.Language
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.CommonClassNames
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiType
import com.intellij.psi.util.InheritanceUtil
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
  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
    // Disable for kotlin for now because retrieving types from expressions doesn't always result in the correct type
    if (holder.file.language == Language.findLanguageByID("kotlin")) return PsiElementVisitor.EMPTY_VISITOR
    return UastHintedVisitorAdapter.create(
      holder.file.language, AssertEqualsBetweenInconvertibleTypesVisitor(holder), arrayOf(UCallExpression::class.java), true
    )
  }
}

private class AssertEqualsBetweenInconvertibleTypesVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
  override fun visitCallExpression(node: UCallExpression): Boolean {
    processAssertHint(createAssertEqualsHint(node), node)
    processAssertHint(createAssertNotEqualsHint(node), node)
    processAssertHint(createAssertSameHint(node), node)
    processAssertHint(createAssertNotSameHint(node), node)
    processAssertJ(node)
    return true
  }

  private fun processAssertHint(assertHint: UAssertHint?, expression: UCallExpression) {
    if (assertHint == null) return
    val firstArgument = assertHint.firstArgument
    val secondArgument = assertHint.secondArgument
    val firstParameter = expression.getParameterForArgument(firstArgument)
    if (firstParameter == null || !TypeUtils.isJavaLangObject(firstParameter.type)) return
    val secondParameter = expression.getParameterForArgument(secondArgument)
    if (secondParameter == null || !TypeUtils.isJavaLangObject(secondParameter.type)) return
    checkConvertibleTypes(expression, firstArgument, secondArgument)
  }

  private fun checkConvertibleTypes(expression: UCallExpression, firstArgument: UExpression, secondArgument: UExpression) {
    if (firstArgument.isNullLiteral() || secondArgument.isNullLiteral()) return
    val type1 = firstArgument.getExpressionType() ?: return
    val type2 = secondArgument.getExpressionType() ?: return
    checkMismatch(expression, type1, type2)
  }

  private fun checkMismatch(expression: UCallExpression, firstType: PsiType, secondType: PsiType) {
    val mismatch = InconvertibleTypesChecker.checkTypes(firstType, secondType, LookForMutualSubclass.IF_CHEAP)
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
    }.asSafely<UCallExpression>()
    val checkType = isEqualsCall?.valueArguments?.firstOrNull()?.getExpressionType() ?: return
    var sourceType = call.valueArguments.firstOrNull()?.getExpressionType() ?: return
    for (elem in chain) {
      if (elem !is UCallExpression) continue
      sourceType = when {
        ASSERTJ_EXTRACTING_REF_MATCHER.uCallMatches(elem) -> return // not supported
        ASSERTJ_EXTRACTING_FUN_MATCHER.uCallMatches(elem) -> return // not supported
        ASSERTJ_EXTRACTING_ITER_FUN_MATCHER.uCallMatches(elem) -> return // not supported
        elem.resolve()?.containingClass?.qualifiedName == "org.assertj.core.api.AbstractThrowableAssert" -> return // not supported
        ASSERTJ_SINGLE_ELEMENT_MATCHER.uCallMatches(elem) || ASSERTJ_FIRST_ELEMENT_MATCHER.uCallMatches(elem) -> {
          if (!InheritanceUtil.isInheritor(sourceType, CommonClassNames.JAVA_LANG_ITERABLE)) return
          sourceType.asSafely<PsiClassType>()?.parameters?.firstOrNull() ?: return
        }
        else -> sourceType
      }
    }
    checkMismatch(isEqualsCall, sourceType, checkType)
  }

  private fun buildErrorString(methodName: String, left: PsiType, right: PsiType): @Nls String {
    val comparedTypeText = left.presentableText
    val comparisonTypeText = right.presentableText
    if (isAssertNotEqualsMethod(methodName)) {
      return JvmAnalysisBundle.message(
        "jvm.inspections.assertnotequals.between.inconvertible.types.problem.descriptor",
        comparedTypeText,
        comparisonTypeText
      )
    }
    return if (isAssertNotSameMethod(methodName)) {
      JvmAnalysisBundle.message(
        "jvm.inspections.assertnotsame.between.inconvertible.types.problem.descriptor",
        comparedTypeText,
        comparisonTypeText
      )
    }
    else JvmAnalysisBundle.message(
      "jvm.inspections.assertequals.between.inconvertible.types.problem.descriptor",
      StringUtil.escapeXmlEntities(comparedTypeText),
      StringUtil.escapeXmlEntities(comparisonTypeText)
    )
  }

  private fun isAssertNotEqualsMethod(methodName: String): Boolean = "assertNotEquals" == methodName || "isNotEqualTo" == methodName

  private fun isAssertNotSameMethod(methodName: String): Boolean = "assertNotSame" == methodName || "isNotSameAs" == methodName

  private companion object {
    private val ASSERTJ_IS_EQUALS_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.Assert", "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs"
    ).parameterTypes(CommonClassNames.JAVA_LANG_OBJECT)

    private val ASSERTJ_EXTRACTING_REF_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractObjectAssert", "extracting"
    ).parameterTypes("java.lang.String")

    private val ASSERTJ_EXTRACTING_FUN_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractObjectAssert", "extracting"
    )

    private val ASSERTJ_SINGLE_ELEMENT_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractIterableAssert", "singleElement"
    )

    private val ASSERTJ_FIRST_ELEMENT_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractIterableAssert", "first"
    )

    private val ASSERTJ_EXTRACTING_ITER_FUN_MATCHER: CallMatcher = CallMatcher.instanceCall(
      "org.assertj.core.api.AbstractIterableAssert", "extracting"
    )

    private val ASSERTJ_ASSERT_THAT_MATCHER: CallMatcher = CallMatcher.staticCall(
      "org.assertj.core.api.Assertions", "assertThat"
    ).parameterCount(1)
  }
}