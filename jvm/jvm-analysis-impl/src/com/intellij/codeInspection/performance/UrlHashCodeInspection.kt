package com.intellij.codeInspection.performance

import com.intellij.analysis.JvmAnalysisBundle
import com.intellij.codeInspection.AbstractBaseUastLocalInspectionTool
import com.intellij.codeInspection.LocalInspectionToolSession
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.codeInspection.registerUProblem
import com.intellij.psi.CommonClassNames.*
import com.intellij.psi.PsiClassType
import com.intellij.psi.PsiElementVisitor
import com.intellij.uast.UastHintedVisitorAdapter
import com.intellij.util.asSafely
import com.siyeh.HardcodedMethodConstants.EQUALS
import com.siyeh.HardcodedMethodConstants.HASH_CODE
import com.siyeh.ig.callMatcher.CallMatcher
import org.jetbrains.uast.*
import org.jetbrains.uast.visitor.AbstractUastNonRecursiveVisitor

class UrlHashCodeInspection : AbstractBaseUastLocalInspectionTool() {
  private fun UExpression.isUrlType() = getExpressionType()?.equalsToText(JAVA_NET_URL) == true

  private fun PsiClassType.isMapType() = rawType().equalsToText(JAVA_UTIL_MAP)

  private fun PsiClassType.isSetType() = rawType().equalsToText(JAVA_UTIL_SET)

  private val mapKeyOperationMatcher: CallMatcher = CallMatcher.instanceCall(
    JAVA_UTIL_MAP,
    "compute", "computeIfAbsent", "computeIfPresent", "containsKey", "get", "getOrDefault", "merge", "put", "putIfAbsent",
    "remove", "replace"
  )

  private val setOperationMatcher: CallMatcher = CallMatcher.instanceCall(JAVA_UTIL_SET, "add", "contains", "equals", "remove")

  private val hashCodeMatcher: CallMatcher = CallMatcher.instanceCall(JAVA_NET_URL, HASH_CODE)
    .parameterCount(0)

  private val equalsMatcher: CallMatcher = CallMatcher.instanceCall(JAVA_NET_URL, EQUALS).parameterTypes(
    JAVA_LANG_OBJECT).withContextFilter {
    val uCallExpression = it.toUElementOfType<UCallExpression>() ?: return@withContextFilter true
    uCallExpression.valueArguments.firstOrNull()?.isNullLiteral()?.not() ?: true
  }

  override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean, session: LocalInspectionToolSession): PsiElementVisitor {
    return UastHintedVisitorAdapter.create(
      holder.file.language,
      UrlHashCodeVisitor(holder),
      arrayOf(UVariable::class.java, UCallExpression::class.java, UCallableReferenceExpression::class.java, UBinaryExpression::class.java),
      true
    )
  }

  private inner class UrlHashCodeVisitor(private val holder: ProblemsHolder) : AbstractUastNonRecursiveVisitor() {
    override fun visitVariable(node: UVariable): Boolean {
      val type = node.type.asSafely<PsiClassType>() ?: return true
      findUrlCollection(type) ?: return true
      holder.registerUProblem(node, JvmAnalysisBundle.message("jvm.inspections.collection.contains.url.problem.descriptor", node.name))
      return true
    }

    private fun findUrlCollection(type: PsiClassType): PsiClassType? {
      if ((type.isMapType() || type.isSetType()) && type.parameters.firstOrNull()?.equalsToText(JAVA_NET_URL) == true) return type
      return type.parameters.firstOrNull { it is PsiClassType && findUrlCollection(it) != null }?.asSafely<PsiClassType>()
    }


    override fun visitBinaryExpression(node: UBinaryExpression): Boolean {
      if (!node.rightOperand.isUrlType() && !node.leftOperand.isUrlType()) return true
      if (node.operatorIdentifier?.name != "==") return true
      if (node.leftOperand.isNullLiteral() || node.rightOperand.isNullLiteral()) return true
      val method = node.resolveOperator() ?: return true
      if (method.name != EQUALS) return true
      val anchor = node.operatorIdentifier?.sourcePsi ?: return true
      holder.registerProblem(
        anchor,
        JvmAnalysisBundle.message("jvm.inspections.equals.hashcode.called.on.url.problem.descriptor", "${method.name}()")
      )
      return true
    }

    override fun visitCallExpression(node: UCallExpression): Boolean {
      if (hashCodeMatcher.uCallMatches(node) || equalsMatcher.uCallMatches(node)) {
        holder.registerUProblem(
          node,
          JvmAnalysisBundle.message("jvm.inspections.equals.hashcode.called.on.url.problem.descriptor", "${node.methodName}()")
        )
        return true
      }
      if (isUrlMapOrSetOperation(node)) {
        val anchor = node.receiver?.sourcePsi ?: node.methodIdentifier?.sourcePsi ?: return true
        val name = if (node.receiver == null) "this" else node.receiver?.sourcePsi?.text ?: return true
        holder.registerProblem(anchor, JvmAnalysisBundle.message("jvm.inspections.collection.contains.url.problem.descriptor", name))
        return true
      }
      return true
    }

    private fun isUrlMapOrSetOperation(node: UCallExpression): Boolean {
      if (!mapKeyOperationMatcher.uCallMatches(node) && !setOperationMatcher.uCallMatches(node)) return false
      return node.valueArguments.firstOrNull()?.isUrlType() == true
    }

    override fun visitCallableReferenceExpression(node: UCallableReferenceExpression): Boolean {
      if (!hashCodeMatcher.uCallableReferenceMatches(node) && !equalsMatcher.uCallableReferenceMatches(node)) return true
      holder.registerUProblem(node, JvmAnalysisBundle.message("jvm.inspections.equals.hashcode.called.on.url.problem.descriptor", "${node.callableName}()"))
      return true
    }
  }
}


