// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.test

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.Language
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.*
import com.intellij.psi.util.InheritanceUtil
import com.intellij.psi.util.PsiUtil
import com.intellij.uast.UastHintedVisitorAdapter
import com.siyeh.ig.callMatcher.CallMatcher
import com.siyeh.ig.psiutils.InconvertibleTypesChecker
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.Convertible
import com.siyeh.ig.psiutils.InconvertibleTypesChecker.LookForMutualSubclass
import com.siyeh.ig.psiutils.StreamApiUtil
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

    // Process asserts from JUnit and TestNG
    processAssertHint(createAssertEqualsHint(node), node)
    processAssertHint(createAssertNotEqualsHint(node), node)
    processAssertHint(createAssertSameHint(node), node)
    processAssertHint(createAssertNotSameHint(node), node)

    // Process asserts from AssertJ
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

  private fun checkMismatch(expression: UCallExpression, firstType: PsiType, secondType: PsiType, originalSourceType: PsiType? = null, originalCheckType: PsiType? = null) {
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
        val errorString = buildErrorString(methodName, originalSourceType ?: mismatch.left, originalCheckType ?: mismatch.right)
        holder.registerProblem(name, errorString, highlightType)
      }
    }
  }

  private fun processAssertJ(call: UCallExpression) {
    if (!ASSERTJ_ASSERT_THAT_MATCHER.uCallMatches(call)) return
    val expressions = call.getOutermostQualified().getQualifiedChain()
    val isEqualsCall = expressions.findLast {
      it is UCallExpression && ASSERTJ_IS_EQUALS_MATCHER.uCallMatches(it)
    } as? UCallExpression
    var checkType = isEqualsCall?.valueArguments?.firstOrNull()?.getExpressionType() ?: return
    val receiverType = GenericsUtil.getVariableTypeByExpressionType(isEqualsCall.receiverType)
    var sourceType = GenericsUtil.getVariableTypeByExpressionType(PsiUtil.substituteTypeParameter(receiverType, ASSERTJ_ASSERT, 1, false)) ?: return
    sourceType = normalizeType(sourceType, receiverType)

    // Handle implicit unwrapping of Stream<T> into List<T>. Learn more at:
    //  https://www.javadoc.io/doc/org.assertj/assertj-core/latest/org/assertj/core/api/Assertions.html#assertThat(java.util.stream.Stream)
    val streamElementType = StreamApiUtil.getStreamElementType(checkType)

    if (streamElementType != null && sourceType is PsiClassType) {
      val sourceClass = sourceType.resolve()
      if (sourceClass != null && sourceClass.qualifiedName == CommonClassNames.JAVA_UTIL_LIST) {
        val boxedElementType = (streamElementType as? PsiPrimitiveType)?.getBoxedType(sourceClass) ?: streamElementType
        val originalCheckType = checkType
        checkType = JavaPsiFacade.getElementFactory(sourceClass.project).createType(sourceClass, boxedElementType)
        val originalStreamSourceType = call.valueArguments.firstOrNull()?.getExpressionType()

        checkMismatch(isEqualsCall, sourceType, checkType, originalSourceType = originalStreamSourceType, originalCheckType = originalCheckType)
        return
      }
    }

    val originalStreamSourceType = call.valueArguments.firstOrNull()?.getExpressionType()
    val sourceTypeWasStream = StreamApiUtil.getStreamElementType(originalStreamSourceType) != null
    val originalSourceType = if (sourceTypeWasStream) originalStreamSourceType else null

    checkMismatch(isEqualsCall, sourceType, checkType, originalSourceType = originalSourceType)
  }

  private fun normalizeType(sourceType: PsiType, receiverType: PsiType?): PsiType {
    val unboxedType = PsiPrimitiveType.getUnboxedType(sourceType)
    if (unboxedType != null && InheritanceUtil.isInheritor(receiverType, "org.assertj.core.api.Abstract${sourceType.presentableText}Assert")) {
      return unboxedType
    }
    return sourceType
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
    private const val ASSERTJ_ASSERT = "org.assertj.core.api.Assert"

    private val ASSERTJ_IS_EQUALS_MATCHER: CallMatcher = CallMatcher.instanceCall(
      ASSERTJ_ASSERT, "isEqualTo", "isSameAs", "isNotEqualTo", "isNotSameAs"
    ).parameterTypes(CommonClassNames.JAVA_LANG_OBJECT)

    private val ASSERTJ_ASSERT_THAT_MATCHER: CallMatcher = CallMatcher.staticCall(
      "org.assertj.core.api.Assertions", "assertThat"
    ).parameterCount(1)
  }
}
