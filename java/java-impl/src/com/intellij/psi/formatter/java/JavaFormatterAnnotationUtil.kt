// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.openapi.roots.LanguageLevelProjectExtension
import com.intellij.pom.java.LanguageLevel
import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.impl.source.tree.JavaElementType
import com.intellij.codeInsight.DumbAwareAnnotationUtil
import com.intellij.psi.util.PsiTreeUtil

internal object JavaFormatterAnnotationUtil {
  private val KNOWN_TYPE_ANNOTATIONS: Set<String> = setOf(
    "org.jetbrains.annotations.NotNull",
    "org.jetbrains.annotations.Nullable"
  )

  /**
   * Checks if the given ASTNode represents a type annotation.
   * This method was designed to handle detection of type annotation during building blocks phase of Java formatter.
   * As there is no resolving available, it relies on local import table and known type annotations.
   * @param annotation the ASTNode to check if it is a type annotation or not.
   * @return true if the ASTNode represents a type annotation, false otherwise
   */
  @JvmStatic
  fun isTypeAnnotation(annotation: ASTNode): Boolean {
    val node = annotation.psi as? PsiAnnotation ?: return false

    val languageLevel = LanguageLevelProjectExtension.getInstance(node.project).languageLevel
    if (languageLevel.isLessThan(LanguageLevel.JDK_1_8)) return false

    val next = PsiTreeUtil.skipSiblingsForward(node, PsiWhiteSpace::class.java, PsiAnnotation::class.java)
    if (next is PsiKeyword) return false

    return KNOWN_TYPE_ANNOTATIONS.any { DumbAwareAnnotationUtil.isAnnotationMatchesFqn(node, it) }
  }

  /**
   * Determines if the given ASTNode represents a Java field with at least one field annotation.
   * @param node the ASTNode to be checked.
   * @return true if the ASTNode represents a field and it has at least one field annotation, false otherwise.
   */
  @JvmStatic
  fun isFieldWithAnnotations(node: ASTNode): Boolean {
    if (node.elementType !== JavaElementType.FIELD) return false

    val modifierList = node.firstChildNode ?: return false
    if (modifierList.elementType != JavaElementType.MODIFIER_LIST) return false
    val annotationList = getAllAnnotationsOnPrefix(modifierList)
    return annotationList.any { !isTypeAnnotation(it) }
  }

  /**
   *  See [isFieldWithAnnotations]
   */
  @JvmStatic
  fun isFieldWithAnnotations(field: PsiModifierListOwner): Boolean {
    return isFieldWithAnnotations(field.node)
  }

  /**
   * Retrieves all annotation nodes within the given modifier list prefix, e.g. before all keywords.
   *
   * @param modifierList the ASTNode representing the modifier list.
   * @return a list of ASTNode objects representing the annotations found in the modifier list.
   */
  private fun getAllAnnotationsOnPrefix(modifierList: ASTNode): List<ASTNode> {
    val result = mutableListOf<ASTNode>()
    var currentNode = modifierList.firstChildNode
    while (currentNode?.elementType == JavaElementType.ANNOTATION) {
      result.add(currentNode)
      currentNode = FormatterUtil.getNextNonWhitespaceSibling(currentNode)
    }
    return result
  }

}