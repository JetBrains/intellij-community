// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter

/**
 * This performs a simple search for properties that must **not** be present.
 * It uses either:
 *   - `{ "not": { "required": ["x"] } }`
 *
 *     most likely to be found in a condition as follows:
 *
 *     `{ "if": { <y> }, "else": { "not": { "required": "x" } } }`
 *
 *     (Read this as: If not `<y>`, must not have property "x")
 *   - Or `{ "if": { "required": { "x" } }, "then": { <y> } }`
 *
 *     (Read this as: If we have property "x", we must `<y>`)
 * @param existingProperties to understand why this knowledge is required, see [com.jetbrains.jsonSchema.impl.JsonBySchemaNotRequiredCompletionTest.test not required x, y and z, then it will not complete the last of the three fields]
 */
internal fun findPropertiesThatMustNotBePresent(
  schema: JsonSchemaObject,
  position: PsiElement,
  project: Project,
  existingProperties: Set<String>,
): Set<String> =
  schema.mapEffectiveSchemasNotNull(position, project) { it.not?.required?.minus(existingProperties)?.singleOrNull() }
    .plusLikelyEmpty(schema.flatMapIfThenElseBranches(position) { ifThenElse, parent ->
      ifThenElse.then?.let { then ->
        ifThenElse.`if`.mapEffectiveSchemasNotNull(position, project) { it.required?.minus(existingProperties)?.singleOrNull() }
          .takeIf { it.isNotEmpty() && parent.adheresTo(then, project) }
      }
    })

/**
 * Traverses the graph of effective schema's and returns a set of all values where [selector] returned a non-null value.
 * Effective schema's includes schema's inside `"allOf"` and `"if" "then" "else"` blocks.
 */
private fun <T: Any> JsonSchemaObject.mapEffectiveSchemasNotNull(
  position: PsiElement,
  project: Project,
  selector: (JsonSchemaObject) -> T?,
): Set<T> =
  setOfNotNull(selector(this))
    .plusLikelyEmpty(allOf?.flatMapLikelyEmpty { it.mapEffectiveSchemasNotNull(position, project, selector) })
    .plusLikelyEmpty(flatMapIfThenElseBranches(position) { ifThenElse, parent ->
      ifThenElse.effectiveBranchOrNull(project, parent)?.mapEffectiveSchemasNotNull(position, project, selector)
    })

private inline fun <T> JsonSchemaObject.flatMapIfThenElseBranches(
  position: PsiElement,
  mapper: (IfThenElse, parent: JsonObjectValueAdapter) -> Set<T>?,
): Set<T> {
  val ifThenElseList: List<IfThenElse> = ifThenElse?.takeIf { it.isNotEmpty() } ?: return emptySet()
  val parent = JsonLikePsiWalker
    .getWalker(position, this)
    ?.getParentPropertyAdapter(position)
    ?.parentObject ?: return emptySet()

  return ifThenElseList.flatMapLikelyEmpty { mapper(it, parent) }
}

internal fun IfThenElse.effectiveBranchOrNull(project: Project, parent: JsonObjectValueAdapter): JsonSchemaObject? =
  if (parent.adheresTo(`if`, project)) then else `else`

private fun JsonObjectValueAdapter.adheresTo(schema: JsonSchemaObject, project: Project): Boolean =
  JsonSchemaAnnotatorChecker(project, JsonComplianceCheckerOptions.RELAX_ENUM_CHECK)
    .also { checker -> checker.checkByScheme(this, schema) }
    .isCorrect

// These allocation optimizations are in place because the checks in this file are often performed, but don't frequently yield results */
private fun <T> Set<T>.plusLikelyEmpty(elements: Set<T>?): Set<T> = when {
  this.isEmpty() -> elements ?: emptySet()
  elements.isNullOrEmpty() -> this
  else -> this + elements
}

private inline fun <T, R> Iterable<T>.flatMapLikelyEmpty(transform: (T) -> Collection<R>?): Set<R> {
  var destination: MutableSet<R>? = null
  for (element in this) {
    val list = transform(element)
    if (list.isNullOrEmpty()) continue

    destination = destination ?: HashSet()
    destination.addAll(list)
  }
  return destination ?: emptySet()
}