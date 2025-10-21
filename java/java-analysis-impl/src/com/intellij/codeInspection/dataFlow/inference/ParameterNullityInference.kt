// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.inference

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.CommonClassNames
import com.intellij.psi.JavaTokenType
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.JavaElementType.*
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.tree.TokenSet
import java.util.*

internal fun inferNotNullParameters(tree: LighterAST, parameterNames: List<String?>, statements: List<LighterASTNode>): BitSet {
  val canBeNulls = parameterNames.filterNotNullTo(HashSet())
  if (canBeNulls.isEmpty()) return BitSet()
  val notNulls = HashSet<String>()
  val queue = ArrayDeque(statements)
  while (queue.isNotEmpty() && canBeNulls.isNotEmpty()) {
    val element = queue.removeFirst()
    when (val type = element.tokenType) {
      CONDITIONAL_EXPRESSION, EXPRESSION_STATEMENT -> JavaLightTreeUtil.findExpressionChild(tree, element)?.let(queue::addFirst)
      RETURN_STATEMENT -> {
        queue.clear()
        JavaLightTreeUtil.findExpressionChild(tree, element)?.let(queue::addFirst)
      }
      FOR_STATEMENT -> {
        val condition = JavaLightTreeUtil.findExpressionChild(tree, element)
        queue.clear()
        if (condition != null) {
          queue.addFirst(condition)
          LightTreeUtil.firstChildOfType(tree, element, ElementType.JAVA_STATEMENT_BIT_SET)?.let(queue::addFirst)
        }
        else {
          // no condition == endless loop: we may analyze body (at least until break/return/if/etc.)
          tree.getChildren(element).asReversed().forEach(queue::addFirst)
        }
      }
      WHILE_STATEMENT -> {
        queue.clear()
        val expression = JavaLightTreeUtil.findExpressionChild(tree, element)
        if (expression?.tokenType == LITERAL_EXPRESSION &&
            LightTreeUtil.firstChildOfType(tree, expression, JavaTokenType.TRUE_KEYWORD) != null) {
          // while(true) == endless loop: we may analyze body (at least until break/return/if/etc.)
          tree.getChildren(element).asReversed().forEach(queue::addFirst)
        } else {
          dereference(tree, expression, canBeNulls, notNulls, queue)
        }
      }
      SWITCH_STATEMENT, SWITCH_EXPRESSION -> {
        queue.clear()
        val expression = JavaLightTreeUtil.findExpressionChild(tree, element)
        val hasExplicitNullCheck = findCaseLabelElementList(tree, element)
          .flatMap { node -> LightTreeUtil.getChildrenOfType(tree, node, LITERAL_EXPRESSION) }
          .any { node -> JavaLightTreeUtil.isNullLiteralExpression(tree, node) }
        if (hasExplicitNullCheck) {
          ignore(tree, expression, canBeNulls)
        }
        else {
          dereference(tree, expression, canBeNulls, notNulls, queue)
        }
      }
      FOREACH_STATEMENT, IF_STATEMENT, THROW_STATEMENT -> {
        queue.clear()
        val expression = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, expression, canBeNulls, notNulls, queue)
      }
      BINARY_EXPRESSION, POLYADIC_EXPRESSION -> {
        if (LightTreeUtil.firstChildOfType(tree, element, TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.OROR)) != null) {
          JavaLightTreeUtil.findExpressionChild(tree, element)?.let(queue::addFirst)
        }
        else {
          tree.getChildren(element).asReversed().forEach(queue::addFirst)
        }
      }
      EMPTY_STATEMENT, ASSERT_STATEMENT, DO_WHILE_STATEMENT, DECLARATION_STATEMENT, BLOCK_STATEMENT -> {
        tree.getChildren(element).asReversed().forEach(queue::addFirst)
      }
      SYNCHRONIZED_STATEMENT -> {
        val sync = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, sync, canBeNulls, notNulls, queue)
        LightTreeUtil.firstChildOfType(tree, element, CODE_BLOCK)?.let(queue::addFirst)
      }
      FIELD, PARAMETER, LOCAL_VARIABLE -> {
        canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, element))
        JavaLightTreeUtil.findExpressionChild(tree, element)?.let(queue::addFirst)
      }
      EXPRESSION_LIST -> {
        val children = JavaLightTreeUtil.getExpressionChildren(tree, element)
        // When parameter is passed to another method, that method may have "null -> fail" contract,
        // so without knowing this we cannot continue inference for the parameter
        children.forEach { ignore(tree, it, canBeNulls) }
        children.asReversed().forEach(queue::addFirst)
      }
      ASSIGNMENT_EXPRESSION -> {
        val lvalue = JavaLightTreeUtil.findExpressionChild(tree, element)
        ignore(tree, lvalue, canBeNulls)
        tree.getChildren(element).asReversed().forEach(queue::addFirst)
      }
      ARRAY_ACCESS_EXPRESSION -> JavaLightTreeUtil.getExpressionChildren(tree, element).forEach {
        dereference(tree, it, canBeNulls, notNulls, queue)
      }
      METHOD_REF_EXPRESSION, REFERENCE_EXPRESSION -> {
        val qualifier = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, qualifier, canBeNulls, notNulls, queue)
      }
      CLASS, METHOD, LAMBDA_EXPRESSION -> {
        // Ignore classes, methods and lambda expression bodies as it's not known whether they will be instantiated/executed.
        // For anonymous classes argument list, field initializers and instance initialization sections are checked.
      }
      TRY_STATEMENT -> {
        queue.clear()
        val canCatchNpe = LightTreeUtil.getChildrenOfType(tree, element, CATCH_SECTION)
          .asSequence()
          .map { LightTreeUtil.firstChildOfType(tree, it, PARAMETER) }
          .filterNotNull()
          .map { parameter -> LightTreeUtil.firstChildOfType(tree, parameter, TYPE) }
          .any { canCatchNpe(tree, it) }
        if (!canCatchNpe) {
          LightTreeUtil.getChildrenOfType(tree, element, RESOURCE_LIST).forEach(queue::addFirst)
          LightTreeUtil.firstChildOfType(tree, element, CODE_BLOCK)?.let(queue::addFirst)
          // stop analysis after first try as we are not sure how execution goes further:
          // whether or not it visit catch blocks, etc.
        }
      }
      else -> {
        if (ElementType.JAVA_STATEMENT_BIT_SET.contains(type)) {
          // Unknown/unprocessed statement: just stop processing the rest of the method
          queue.clear()
        }
        else {
          tree.getChildren(element).asReversed().forEach(queue::addFirst)
        }
      }
    }
  }
  val notNullParameters = BitSet()
  parameterNames.forEachIndexed { index, s -> if (notNulls.contains(s)) notNullParameters.set(index) }
  return notNullParameters
}

private val NPE_CATCHERS = setOf("Throwable", "Exception", "RuntimeException", "NullPointerException",
                                 CommonClassNames.JAVA_LANG_THROWABLE, CommonClassNames.JAVA_LANG_EXCEPTION,
                                 CommonClassNames.JAVA_LANG_RUNTIME_EXCEPTION, CommonClassNames.JAVA_LANG_NULL_POINTER_EXCEPTION)

public fun canCatchNpe(tree: LighterAST, type: LighterASTNode?): Boolean {
  if (type == null) return false
  val codeRef = LightTreeUtil.firstChildOfType(tree, type, JAVA_CODE_REFERENCE)
  val name = JavaLightTreeUtil.getNameIdentifierText(tree, codeRef)
  if (name == null) {
    // Multicatch
    return LightTreeUtil.getChildrenOfType(tree, type, TYPE).any { canCatchNpe(tree, it) }
  }
  return NPE_CATCHERS.contains(name)
}

private fun ignore(tree: LighterAST,
                   expression: LighterASTNode?,
                   canBeNulls: HashSet<String>) {
  val stripped = JavaLightTreeUtil.skipParenthesesDown(tree, expression)
  if (stripped != null &&
      stripped.tokenType == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, stripped) == null) {
    canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, stripped))
  }
}

private fun dereference(tree: LighterAST,
                        expression: LighterASTNode?,
                        canBeNulls: HashSet<String>,
                        notNulls: HashSet<String>,
                        queue: ArrayDeque<LighterASTNode>) {
  val stripped = JavaLightTreeUtil.skipParenthesesDown(tree, expression)
  if (stripped == null) return
  if (stripped.tokenType == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, stripped) == null) {
    JavaLightTreeUtil.getNameIdentifierText(tree, stripped)?.takeIf(canBeNulls::remove)?.let(notNulls::add)
  }
  else {
    queue.addFirst(stripped)
  }
}

/**
 * Returns list of parameter names. A null in returned list means that either parameter name
 * is absent in the source or it's a primitive type (thus nullity inference does not apply).
 */
internal fun getParameterNames(tree: LighterAST, method: LighterASTNode): List<String?> {
  val parameterList = LightTreeUtil.firstChildOfType(tree, method, PARAMETER_LIST) ?: return emptyList()
  val parameters = LightTreeUtil.getChildrenOfType(tree, parameterList, PARAMETER)
  return parameters.map {
    if (LightTreeUtil.firstChildOfType(tree, it, ElementType.PRIMITIVE_TYPE_BIT_SET) != null) null
    else JavaLightTreeUtil.getNameIdentifierText(tree, it)
  }
}

private fun findCaseLabelElementList(tree: LighterAST, switchNode: LighterASTNode): List<LighterASTNode> {
  val codeBlock = LightTreeUtil.firstChildOfType(tree, switchNode, CODE_BLOCK) ?: return emptyList()
  var rules: List<LighterASTNode> = LightTreeUtil.getChildrenOfType(tree, codeBlock, SWITCH_LABELED_RULE)
  rules += LightTreeUtil.getChildrenOfType(tree, codeBlock, SWITCH_LABEL_STATEMENT)
  return rules.mapNotNull { node -> LightTreeUtil.firstChildOfType(tree, node, CASE_LABEL_ELEMENT_LIST) }
}
