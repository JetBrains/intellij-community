// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.json.pointer.JsonPointerPosition
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
import com.jetbrains.jsonSchema.extension.JsonLikePsiWalker
import com.jetbrains.jsonSchema.extension.adapters.JsonObjectValueAdapter
import com.jetbrains.jsonSchema.extension.adapters.JsonValueAdapter
import com.jetbrains.jsonSchema.impl.JsonSchemaObject
import com.jetbrains.jsonSchema.impl.JsonSchemaResolver

/**
 * Collects nested completions for a JSON schema object.
 * If `[node] == null`, it will just call collector once.
 *
 * @param project The project where the JSON schema is being used.
 * @param node A tree structure that represents a path through which we want nested completions.
 * @param completionPath The path of the completion in the schema.
 * @param collector The callback function to collect the nested completions.
 */
internal fun JsonSchemaObject.collectNestedCompletions(
  project: Project,
  node: NestedCompletionsNode?,
  completionPath: SchemaPath?,
  collector: (path: SchemaPath?, schema: JsonSchemaObject) -> Unit,
) {
  collector(completionPath, this) // Breadth first

  node
    ?.children
    ?.filterIsInstance<ChildNode.OpenNode>()
    ?.forEach { (name, childNode) ->
      for (subSchema in findSubSchemasByName(project, name)) {
        subSchema.collectNestedCompletions(project, childNode, completionPath / name, collector)
      }
    }
}

private fun JsonSchemaObject.findSubSchemasByName(project: Project, name: String): Iterable<JsonSchemaObject> =
  JsonSchemaResolver(project, this, JsonPointerPosition().apply { addFollowingStep(name) }).resolve()

internal fun CharSequence.mark(index: Int): String = substring(0, index) + "|" + substring(index)  // TODO: Remove, debugging only!
internal fun CharSequence.mark(vararg indices: Int): String = indices.sortedDescending()  // TODO: Remove, debugging only!
  .fold(toString()) { acc, mark -> acc.mark(mark) } // TODO: Remove, debugging only!


internal fun JsonLikePsiWalker.findChildBy(path: SchemaPath?, start: PsiElement): PsiElement =
  path?.let {
    findContainingObjectAdapter(start)
      ?.findChildBy(path.accessor(), offset = 0)
      ?.delegate
  } ?: start

private fun JsonLikePsiWalker.findContainingObjectAdapter(start: PsiElement) =
  start.parents(true).firstNotNullOfOrNull { createValueAdapter(it)?.asObject }

internal tailrec fun JsonObjectValueAdapter.findChildBy(path: List<String>, offset: Int): JsonValueAdapter? =
  if(offset > path.lastIndex) this
  else childByName(path[offset])
    ?.asObject
    ?.findChildBy(path, offset + 1)

private fun JsonObjectValueAdapter.childByName(name: String): JsonValueAdapter? =
  propertyList.firstOrNull { it.name == name }
    ?.values
    ?.firstOrNull()
