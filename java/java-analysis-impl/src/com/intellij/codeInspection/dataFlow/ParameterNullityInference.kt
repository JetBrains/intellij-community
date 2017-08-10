/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInspection.dataFlow

import com.intellij.lang.LighterAST
import com.intellij.lang.LighterASTNode
import com.intellij.psi.JavaTokenType
import com.intellij.psi.impl.source.JavaLightTreeUtil
import com.intellij.psi.impl.source.tree.ElementType
import com.intellij.psi.impl.source.tree.JavaElementType.*
import com.intellij.psi.impl.source.tree.LightTreeUtil
import com.intellij.psi.tree.TokenSet
import java.util.*

fun inferNotNullParameters(tree: LighterAST, method: LighterASTNode, statements: List<LighterASTNode>): BitSet {
  val parameterNames = getParameterNames(tree, method)
  return inferNotNullParameters(tree, parameterNames, statements)
}

private fun inferNotNullParameters(tree: LighterAST, parameterNames: List<String?>, statements: List<LighterASTNode>): BitSet {
  val canBeNulls = parameterNames.filterNotNullTo(HashSet())
  if (canBeNulls.isEmpty()) return BitSet()
  val notNulls = HashSet<String>()
  val queue = ArrayDeque<LighterASTNode>(statements)
  while (queue.isNotEmpty() && canBeNulls.isNotEmpty()) {
    val element = queue.removeFirst()
    val type = element.tokenType
    when (type) {
      CONDITIONAL_EXPRESSION, EXPRESSION_STATEMENT -> JavaLightTreeUtil.findExpressionChild(tree, element)?.let { queue.addFirst(it) }
      RETURN_STATEMENT -> {
        queue.clear()
        JavaLightTreeUtil.findExpressionChild(tree, element)?.let { queue.addFirst(it) }
      }
      FOR_STATEMENT -> {
        val condition = JavaLightTreeUtil.findExpressionChild(tree, element)
        queue.clear()
        if (condition != null) {
          queue.addFirst(condition)
          LightTreeUtil.firstChildOfType(tree, element, ElementType.JAVA_STATEMENT_BIT_SET)?.let { queue.addFirst(it) }
        }
        else {
          // no condition == endless loop: we may analyze body (at least until break/return/if/etc.)
          tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
        }
      }
      WHILE_STATEMENT -> {
        queue.clear()
        val expression = JavaLightTreeUtil.findExpressionChild(tree, element)
        if (expression?.tokenType == LITERAL_EXPRESSION &&
            LightTreeUtil.firstChildOfType(tree, expression, JavaTokenType.TRUE_KEYWORD) != null) {
          // while(true) == endless loop: we may analyze body (at least until break/return/if/etc.)
          tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
        } else {
          dereference(tree, expression, canBeNulls, notNulls, queue)
        }
      }
      FOREACH_STATEMENT, SWITCH_STATEMENT, IF_STATEMENT, THROW_STATEMENT -> {
        queue.clear()
        val expression = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, expression, canBeNulls, notNulls, queue)
      }
      BINARY_EXPRESSION, POLYADIC_EXPRESSION -> {
        if (LightTreeUtil.firstChildOfType(tree, element, TokenSet.create(JavaTokenType.ANDAND, JavaTokenType.OROR)) != null) {
          JavaLightTreeUtil.findExpressionChild(tree, element)?.let { queue.addFirst(it) }
        }
        else {
          tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
        }
      }
      EMPTY_STATEMENT, ASSERT_STATEMENT, DO_WHILE_STATEMENT, DECLARATION_STATEMENT, BLOCK_STATEMENT -> {
        tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
      }
      SYNCHRONIZED_STATEMENT -> {
        val sync = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, sync, canBeNulls, notNulls, queue)
        LightTreeUtil.firstChildOfType(tree, element, CODE_BLOCK)?.let { queue.addFirst(it) }
      }
      FIELD, PARAMETER, LOCAL_VARIABLE -> {
        canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, element))
        JavaLightTreeUtil.findExpressionChild(tree, element)?.let { queue.addFirst(it) }
      }
      EXPRESSION_LIST -> {
        val children = JavaLightTreeUtil.getExpressionChildren(tree, element)
        // When parameter is passed to another method, that method may have "null -> fail" contract,
        // so without knowing this we cannot continue inference for the parameter
        children.forEach { ignore(tree, it, canBeNulls) }
        children.asReversed().forEach { queue.addFirst(it) }
      }
      ASSIGNMENT_EXPRESSION -> {
        val lvalue = JavaLightTreeUtil.findExpressionChild(tree, element)
        ignore(tree, lvalue, canBeNulls)
        tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
      }
      ARRAY_ACCESS_EXPRESSION -> JavaLightTreeUtil.getExpressionChildren(tree, element).forEach {
        dereference(tree, it, canBeNulls, notNulls, queue)
      }
      METHOD_REF_EXPRESSION, REFERENCE_EXPRESSION -> {
        val qualifier = JavaLightTreeUtil.findExpressionChild(tree, element)
        dereference(tree, qualifier, canBeNulls, notNulls, queue)
      }
      CLASS, METHOD, LAMBDA_EXPRESSION -> {
        // ignore classes, methods and lambda expression bodies as it's not known whether they will be instantiated/executed
        // for anonymous classes argument list, field initializers and instance initialization sections are checked
      }
      else -> {
        if (ElementType.JAVA_STATEMENT_BIT_SET.contains(type)) {
          // Unknown/unprocessed statement: just stop processing the rest of the method
          queue.clear()
        }
        else {
          tree.getChildren(element).asReversed().forEach { queue.addFirst(it) }
        }
      }
    }
  }
  val notNullParameters = BitSet()
  parameterNames.forEachIndexed { index, s -> if (notNulls.contains(s)) notNullParameters.set(index) }
  return notNullParameters
}

private fun ignore(tree: LighterAST,
                   expression: LighterASTNode?,
                   canBeNulls: HashSet<String>) {
  if (expression != null &&
      expression.tokenType == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, expression) == null) {
    canBeNulls.remove(JavaLightTreeUtil.getNameIdentifierText(tree, expression))
  }
}

private fun dereference(tree: LighterAST,
                        expression: LighterASTNode?,
                        canBeNulls: HashSet<String>,
                        notNulls: HashSet<String>,
                        queue: ArrayDeque<LighterASTNode>) {
  if (expression == null) return
  if (expression.tokenType == REFERENCE_EXPRESSION && JavaLightTreeUtil.findExpressionChild(tree, expression) == null) {
    val name = JavaLightTreeUtil.getNameIdentifierText(tree, expression)
    if (name != null && canBeNulls.remove(name)) {
      notNulls.add(name)
    }
  }
  else {
    queue.addFirst(expression)
  }
}

/**
 * Returns list of parameter names. A null in returned list means that either parameter name
 * is absent in the source or it's a primitive type (thus nullity inference does not apply).
 */
private fun getParameterNames(tree: LighterAST, method: LighterASTNode): List<String?> {
  val parameterList = LightTreeUtil.firstChildOfType(tree, method, PARAMETER_LIST) ?: return ArrayList()
  val parameters = LightTreeUtil.getChildrenOfType(tree, parameterList, PARAMETER)
  return parameters.mapTo(ArrayList()) {
    if (LightTreeUtil.firstChildOfType(tree, it, ElementType.PRIMITIVE_TYPE_BIT_SET) != null) null
    else JavaLightTreeUtil.getNameIdentifierText(tree, it)
  }
}

