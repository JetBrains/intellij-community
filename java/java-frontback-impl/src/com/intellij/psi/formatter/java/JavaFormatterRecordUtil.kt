// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.psi.JavaTokenType
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.codeStyle.JavaCodeStyleSettings
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.tree.JavaElementType


object JavaFormatterRecordUtil {
  /**
   * In some option configurations, the formatter removes prefix spaces from the record components, consider this java example:
   * ```java
   * public record(@FirstAnno @SecondAnno String s) {}
   * ```
   * might be formatted to
   * ```java
   * public record(@FirstAnno
   * @SecondAnno String s) {
   * }
   * ```
   * This method helps to detect such cases based on the given [node]
   */
  @JvmStatic
  fun shouldAdjustIndentForRecordComponentChild(node: ASTNode, javaSettings: JavaCodeStyleSettings): Boolean {
    if(javaSettings.NEW_LINE_AFTER_LPAREN_IN_RECORD_HEADER ||
       javaSettings.ALIGN_MULTILINE_RECORDS ||
       javaSettings.RECORD_COMPONENTS_WRAP != CommonCodeStyleSettings.WRAP_ALWAYS ||
       !javaSettings.ANNOTATION_NEW_LINE_IN_RECORD_COMPONENT) return false

    return isInFirstRecordComponent(node)
  }

  /**
   * Checks if the given [node] belongs to the record component. Node might be either an annotation or a type element.
   */
  @JvmStatic
  fun isInRecordComponent(node: ASTNode): Boolean = findRecordComponent(node) != null

  private fun isInFirstRecordComponent(node: ASTNode): Boolean {
    val parent = findRecordComponent(node) ?: return false
    val prev = FormatterUtil.getPreviousNonWhitespaceSibling(parent)
    return prev?.elementType == JavaTokenType.LPARENTH
  }

  private fun findRecordComponent(node : ASTNode): ASTNode? {
    val parent = node.treeParent ?: return null
    if (parent.elementType == JavaElementType.RECORD_COMPONENT) return parent
    else if (parent.elementType != JavaElementType.MODIFIER_LIST) return null

    val grandParent = parent.treeParent ?: return null
    return if (grandParent.elementType == JavaElementType.RECORD_COMPONENT) grandParent else null
  }
}