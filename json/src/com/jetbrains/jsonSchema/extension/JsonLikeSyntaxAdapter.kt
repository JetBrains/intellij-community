// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.impl.JsonSchemaType

interface JsonLikeSyntaxAdapter {
  fun adjustValue(value: PsiElement): PsiElement = value

  fun createProperty(name: String, value: String, element: PsiElement): PsiElement
  fun ensureComma(self: PsiElement?, newElement: PsiElement?)
  fun removeIfComma(forward: PsiElement?)
  fun fixWhitespaceBefore(initialElement: PsiElement?, element: PsiElement?): Boolean
  fun getDefaultValueFromType(type: JsonSchemaType?): String = if (type == null) "" else type.defaultValue

  fun adjustNewProperty(element: PsiElement): PsiElement
  fun adjustPropertyAnchor(element: LeafPsiElement): PsiElement

  /**
   * Inserts a property into an existing object
   * @param contextForInsertion either the object itself or the property before which the new property is inserted
   * @param newProperty property to insert
   * @return inserted property
   */
  fun addProperty(contextForInsertion: PsiElement, newProperty: PsiElement): PsiElement {
    val walker = JsonLikePsiWalker.getWalker(contextForInsertion)
    val parentPropertyAdapter = walker?.getParentPropertyAdapter(contextForInsertion)
    val isProcessingProperty = parentPropertyAdapter != null && parentPropertyAdapter.delegate === contextForInsertion

    val newElement = when {
      contextForInsertion is LeafPsiElement -> {
        adjustPropertyAnchor(contextForInsertion).addBefore(newProperty, null)
      }
      isProcessingProperty -> {
        contextForInsertion.parent.addBefore(newProperty, contextForInsertion)
      }
      else -> {
        contextForInsertion.addBefore(newProperty, contextForInsertion.lastChild)
      }
    }
    val adjusted = adjustNewProperty(newElement)

    ensureComma(adjusted, PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement))
    ensureComma(PsiTreeUtil.skipWhitespacesAndCommentsBackward(newElement), adjusted)

    return adjusted
  }

  /**
   * Deletes a property performing the cleanup if needed
   * @param property property to delete
   */
  fun removeProperty(property: PsiElement) {
    val forward = PsiTreeUtil.skipWhitespacesForward(property)
    property.delete()
    removeIfComma(forward)
  }
}
