// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter.java

import com.intellij.lang.ASTNode
import com.intellij.lang.tree.util.children
import com.intellij.psi.PsiModifierListOwner
import com.intellij.psi.impl.source.tree.JavaElementType

object AnnotationUtil {
  @JvmStatic
  fun isFieldWithAnnotations(node: ASTNode): Boolean {
    if (node.elementType !== JavaElementType.FIELD) return false

    val modifierList = node.firstChildNode ?: return false
    if (modifierList.elementType != JavaElementType.MODIFIER_LIST) return false
    val annotations = modifierList.children().takeWhile { it.elementType == JavaElementType.ANNOTATION }
    return annotations.any { !TypeAnnotationUtil.isTypeAnnotation(it) }
  }

  @JvmStatic
  fun isFieldWithAnnotations(field: PsiModifierListOwner): Boolean {
    return isFieldWithAnnotations(field.node)
  }
}