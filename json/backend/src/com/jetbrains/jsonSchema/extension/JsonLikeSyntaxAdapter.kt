// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.extension

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafPsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.jetbrains.jsonSchema.impl.JsonSchemaType

interface JsonLikeSyntaxAdapter {
  /**
   * Tries to get a property value element for a context element supplied.
   * May modify PSI and create a new property value element if it wasn't there before.
   */
  fun adjustValue(value: PsiElement): PsiElement = value

  /**
   * Creates a property element with the given [name] and [value].
   *
   * The created element is in an independent PSI tree, and is meant to be inserted in the target tree through some mutating operation,
   * such as [PsiElement.replace]. The given [project] is used as context for PSI generation
   */
  fun createProperty(name: String, value: String, project: Project): PsiElement

  /**
   * Creates an empty array element.
   * 
   * In languages that support single-line and multi-line arrays, [preferInline] determines whether to use the single-line form.
   *
   * The created element is in an independent PSI tree, and is meant to be inserted in the target tree through some mutating operation,
   * such as [PsiElement.replace]. The given [project] is used as context for PSI generation
   */
  fun createEmptyArray(project: Project, preferInline: Boolean = true): PsiElement

  /**
   * Adds the given [itemValue] to the given [array] element.
   * 
   * @return the [PsiElement] representing the item that was added.
   */
  fun addArrayItem(array: PsiElement, itemValue: String): PsiElement
  fun removeArrayItem(item: PsiElement)
  
  fun ensureComma(self: PsiElement?, newElement: PsiElement?)
  fun removeIfComma(forward: PsiElement?)
  fun fixWhitespaceBefore(initialElement: PsiElement?, element: PsiElement?): Boolean
  fun getDefaultValueFromType(type: JsonSchemaType?): String = if (type == null) "" else type.defaultValue

  fun adjustNewProperty(element: PsiElement): PsiElement
  fun adjustPropertyAnchor(element: LeafPsiElement): PsiElement

  /**
   * Inserts a property into a JSON-like object. If the given [contextForInsertion] is an empty value that can act as an object (such as an
   * empty YAML Document or a simple indent in the position of a property value), the property is inserted as part of a new object.
   *
   * @param contextForInsertion either the object itself or the property before which the new property is inserted. It can also be an empty
   * object value (such as an empty YAML Document or a simple indent in the position of a property value).
   * @param newProperty the property element to insert
   *
   * @return the property element that was actually added (either [newProperty] or its copy).
   */
  fun addProperty(contextForInsertion: PsiElement, newProperty: PsiElement): PsiElement {
    val walker = JsonLikePsiWalker.getWalker(contextForInsertion)
    val parentPropertyAdapter = walker?.getParentPropertyAdapter(contextForInsertion)
    val isProcessingProperty = parentPropertyAdapter != null && parentPropertyAdapter.delegate === contextForInsertion

    val newElement = when {
      walker?.createValueAdapter(contextForInsertion)?.isEmptyAdapter == true -> {
        val parent = if (contextForInsertion is LeafPsiElement) adjustPropertyAnchor(contextForInsertion) else contextForInsertion
        // This newProperty.parent relies on the fact that the property was created within an object in its dummy environment.
        // This might not always hold, so it would be better not to rely on it.
        parent.addBefore(newProperty.parent, null)
      }
      contextForInsertion is LeafPsiElement -> {
        adjustPropertyAnchor(contextForInsertion).addBefore(newProperty, null)
      }
      isProcessingProperty -> {
        contextForInsertion.parent.addBefore(newProperty, contextForInsertion).also {
          ensureComma(PsiTreeUtil.skipWhitespacesAndCommentsBackward(it), it)
        }
      }
      else -> {
        // In case of an object, we want to insert after the last property and potential comments, but before whatever syntax marks the end
        // of the object (e.g. a } in JSON, but nothing in YAML). We can't just use 'contextForInsertion.lastChild' because it's actually
        // the last property itself in YAML (since there is no syntax for closing the object).
        val objectAdapter = walker?.createValueAdapter(contextForInsertion)?.asObject
                            ?: error("contextForInsertion must be an object-like element")
        val lastPropertyElement = objectAdapter.propertyList.lastOrNull()?.delegate
        val lastElementInsideObject = if (lastPropertyElement != null) {
          PsiTreeUtil.skipWhitespacesAndCommentsForward(lastPropertyElement)
        }
        else {
          contextForInsertion.lastChild
        } 
        contextForInsertion.addBefore(newProperty, lastElementInsideObject).also {
          ensureComma(lastPropertyElement, it)
        }
      }
    }
    val adjusted = adjustNewProperty(newElement)

    ensureComma(adjusted, PsiTreeUtil.skipWhitespacesAndCommentsForward(newElement))

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
