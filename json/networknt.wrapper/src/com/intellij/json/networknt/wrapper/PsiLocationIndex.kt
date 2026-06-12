// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonArrayValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.networknt.schema.path.NodePath
import com.networknt.schema.path.PathType
import org.jetbrains.annotations.TestOnly

/**
 * Maps JSON pointer paths to PSI elements for error placement.
 *
 * This index enables networknt validation errors (which use [NodePath])
 * to be mapped to specific PSI elements for IDE highlighting.
 *
 * The index is built by walking the PSI tree depth-first and recording
 * the mapping from JSON pointer strings to PSI elements.
 */
class PsiLocationIndex private constructor(
  private val pointerToElement: MutableMap<String, PsiElement>,
  private val propertyElements: MutableMap<String, PropertyElements>,
  private val suppressedTargets: MutableSet<PsiElement>,
) {
  internal data class PropertyElements(
    val nameElement: PsiElement?,
    val valueElement: PsiElement?
  )

  companion object {
    /**
     * Factory method to build and return the index.
     *
     * @param walker The language-specific PSI walker (JSON or YAML)
     * @param rootElement The root PSI element to index
     * @return A fully built index ready for resolution
     */
    fun build(walker: JsonLikePsiWalker, rootElement: PsiElement): PsiLocationIndex =
      build(walker, rootElement, defaultPropertyValueSelector)

    internal fun build(
      walker: JsonLikePsiWalker,
      rootElement: PsiElement,
      selector: PropertyValueSelector,
    ): PsiLocationIndex {
      val pointerToElement: MutableMap<String, PsiElement> = mutableMapOf()
      val propertyElements: MutableMap<String, PropertyElements> = mutableMapOf()
      val suppressedTargets: MutableSet<PsiElement> = mutableSetOf()
      buildIndex(walker, rootElement, pointerToElement, propertyElements, suppressedTargets, selector)
      return PsiLocationIndex(pointerToElement, propertyElements, suppressedTargets)
    }

    @TestOnly
    fun empty(): PsiLocationIndex = PsiLocationIndex(mutableMapOf(), mutableMapOf(), mutableSetOf())

    private fun buildIndex(
      walker: JsonLikePsiWalker,
      rootElement: PsiElement,
      pointerToElement: MutableMap<String, PsiElement>,
      propertyElements: MutableMap<String, PropertyElements>,
      suppressedTargets: MutableSet<PsiElement>,
      selector: PropertyValueSelector,
    ) {
      // NetworkNT uses PathType.JSON_POINTER by default (starts with "/")
      val rootPath = NodePath(PathType.JSON_POINTER)

      // Index the root element itself
      pointerToElement[rootPath.toString()] = rootElement

      // Create adapter and recurse
      val rootAdapter = walker.createValueAdapter(rootElement)
      if (rootAdapter != null) {
        walkValue(walker, rootAdapter, rootPath, pointerToElement, propertyElements, suppressedTargets, selector)
      }
    }

    private fun walkValue(
      walker: JsonLikePsiWalker,
      valueAdapter: JsonValueAdapter,
      currentPath: NodePath,
      pointerToElement: MutableMap<String, PsiElement>,
      propertyElements: MutableMap<String, PropertyElements>,
      suppressedTargets: MutableSet<PsiElement>,
      selector: PropertyValueSelector,
    ) {
      val element = valueAdapter.delegate

      // Index this value at current path
      pointerToElement[currentPath.toString()] = element

      // Adapters that opt out of value-level validation (JS refs/calls/new/IIFE — see
      // JSJsonForeignValueAdapter) get their PSI delegate registered as suppressed so
      // [NetworkntErrorMapper] can drop networknt errors targeting them. The PsiToJsonNode
      // converter emits a placeholder for these, so we also stop indexing the opaque subtree —
      // its descendants are not real instance values.
      if (!valueAdapter.shouldCheckAsValue()) {
        suppressedTargets.add(element)
        return
      }

      when {
        valueAdapter.isObject -> {
          val objectAdapter = valueAdapter.asObject
          if (objectAdapter != null) {
            walkObject(walker, objectAdapter, currentPath, pointerToElement, propertyElements, suppressedTargets, selector)
          }
        }
        valueAdapter.isArray -> {
          val arrayAdapter = valueAdapter.asArray
          if (arrayAdapter != null) {
            walkArray(walker, arrayAdapter, currentPath, pointerToElement, propertyElements, suppressedTargets, selector)
          }
        }
        // Primitive values (string, number, boolean, null) - already indexed above
      }
    }

    private fun walkObject(
      walker: JsonLikePsiWalker,
      objectAdapter: JsonObjectValueAdapter,
      currentPath: NodePath,
      pointerToElement: MutableMap<String, PsiElement>,
      propertyElements: MutableMap<String, PropertyElements>,
      suppressedTargets: MutableSet<PsiElement>,
      selector: PropertyValueSelector,
    ) {
      for (propertyAdapter in objectAdapter.propertyList) {
        val propertyName = propertyAdapter.name ?: continue // Skip properties without names
        val propertyPath = currentPath.append(propertyName)

        // Get property PSI elements
        val propertyElement = propertyAdapter.delegate
        val nameElement = walker.getPropertyNameElement(propertyElement)

        val chosenValue = selector(propertyAdapter)

        // Store fine-grained property elements for errors like "additionalProperties"
        propertyElements[propertyPath.toString()] = PropertyElements(
          nameElement = nameElement,
          valueElement = chosenValue?.delegate
        )

        // Index the property value at this path and recurse
        if (chosenValue != null) {
          walkValue(walker, chosenValue, propertyPath, pointerToElement, propertyElements, suppressedTargets, selector)
        }
      }
    }

    private fun walkArray(
      walker: JsonLikePsiWalker,
      arrayAdapter: JsonArrayValueAdapter,
      currentPath: NodePath,
      pointerToElement: MutableMap<String, PsiElement>,
      propertyElements: MutableMap<String, PropertyElements>,
      suppressedTargets: MutableSet<PsiElement>,
      selector: PropertyValueSelector,
    ) {
      arrayAdapter.elements.forEachIndexed { index, elementAdapter ->
        val elementPath = currentPath.append(index)
        walkValue(walker, elementAdapter, elementPath, pointerToElement, propertyElements, suppressedTargets, selector)
      }
    }
  }

  /**
   * Resolves a [NodePath] to its corresponding PSI element.
   * Returns the value element at that path.
   *
   * @param instanceLocation The path to resolve
   * @return The PSI element at that path, or null if not found
   */
  fun resolve(instanceLocation: NodePath): PsiElement? {
    return pointerToElement[instanceLocation.toString()]
  }

  /**
   * Resolves to the property NAME element for errors like "additionalProperties".
   *
   * @param parentPath Path to the parent object
   * @param propertyName Name of the property
   * @return The property name PSI element (e.g., JsonStringLiteral for the key)
   */
  fun resolvePropertyName(parentPath: NodePath, propertyName: String): PsiElement? {
    val propertyPath = parentPath.append(propertyName).toString()
    return propertyElements[propertyPath]?.nameElement
  }

  /**
   * Resolves to the property VALUE element for errors like "type", "enum", "const".
   *
   * @param parentPath Path to the parent object
   * @param propertyName Name of the property
   * @return The property value PSI element
   */
  fun resolvePropertyValue(parentPath: NodePath, propertyName: String): PsiElement? {
    val propertyPath = parentPath.append(propertyName).toString()
    return propertyElements[propertyPath]?.valueElement
      ?: pointerToElement[propertyPath] // Fallback to general index
  }

  /**
   * Whether the given PSI element belongs to a value the language adapter marked as opaque
   * via [JsonValueAdapter.shouldCheckAsValue] (e.g. JS reference/call/new expressions). The
   * networknt path emits a placeholder JsonNode for these values; any error networknt
   * subsequently attributes to the same PSI is a false positive and should be dropped.
   */
  fun isSuppressed(psi: PsiElement): Boolean = psi in suppressedTargets

  /**
   * Add a PSI element to the suppressed set after the index is built. Test-only seam for
   * exercising [NetworkntErrorMapper]'s suppression filter without needing a language-specific
   * adapter that returns `shouldCheckAsValue == false` (only JS adapters do today).
   */
  @TestOnly
  fun markSuppressedForTest(psi: PsiElement) {
    suppressedTargets.add(psi)
  }
}
