// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.json.pointer.JsonPointerPosition
import org.jetbrains.annotations.ApiStatus

/**
 * Represents a tree structure of how completions can be nested through a schema.
 *
 * If you request completions in a configuration node that has a corresponding [com.jetbrains.jsonSchema.impl.JsonSchemaObject]
 * as well as a corresponding [NestedCompletionsNode], you will see completions for the entire subtree of the [NestedCompletionsNode]
 *
 * See tests for details
 */
@ApiStatus.Experimental
class NestedCompletionsNode internal constructor(val isolated: Boolean, val children: Map<String, NestedCompletionsNode>)

@ApiStatus.Experimental
fun buildNestedCompletionsTree(block: NestedCompletionsNodeBuilder.() -> Unit): NestedCompletionsNode = buildNestedCompletionsTree(false, block)

private fun buildNestedCompletionsTree(isolated: Boolean, block: NestedCompletionsNodeBuilder.() -> Unit): NestedCompletionsNode =
  NestedCompletionsNode(
    isolated = isolated,
    children = buildMap {
      block(
        object : NestedCompletionsNodeBuilder {
          override fun isolated(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit) {
            this@buildMap[name] = buildNestedCompletionsTree(true, childBuilder)
          }

          override fun open(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit) {
            this@buildMap[name] = buildNestedCompletionsTree(false, childBuilder)
          }
        }
      )
    }
  )

@ApiStatus.Experimental
interface NestedCompletionsNodeBuilder {
  /**
   * Constructs a tree node that will not forward nested completions from outside down into it's children
   * By default, all completions are `isolated("<name>") {}`
   */
  fun isolated(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit)

  /** Constructs a node that allows completions from outside to nest into this node */
  fun open(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit = {})
}

internal fun NestedCompletionsNode?.merge(other: NestedCompletionsNode?): NestedCompletionsNode? = when {
  this == null -> other
  other == null -> this
  else -> this.merge(other)
}

@JvmName("MergeNotNull")
private fun NestedCompletionsNode.merge(other: NestedCompletionsNode): NestedCompletionsNode =
  NestedCompletionsNode(isolated && other.isolated, children.merge(other.children, NestedCompletionsNode::merge))

internal fun NestedCompletionsNode?.navigate(jsonPointer: JsonPointerPosition): NestedCompletionsNode? =
  this?.navigate(0, jsonPointer.toPathItems())

private fun JsonPointerPosition.toPathItems() =
  toJsonPointer()?.takeIf { it != "/" }?.drop(1)?.split("/") ?: emptyList()

private tailrec fun NestedCompletionsNode.navigate(index: Int, steps: List<String>): NestedCompletionsNode? =
  if (index !in steps.indices) this else children[steps[index]]?.navigate(index + 1, steps)

private fun <K, V : Any> Map<K, V>.merge(other: Map<K, V>, remappingFunction: (V, V) -> V): Map<K, V> = toMutableMap().apply {
  for ((key, value) in other) {
    this.merge(key, value, remappingFunction)
  }
}