// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.nestedCompletions

import com.intellij.json.pointer.JsonPointerPosition
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface NestedCompletionsNodeBuilder {
  /**
   * Constructs a tree node that will not forward nested completions from outside down into it's children
   */
  fun isolated(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit)

  /**
   * Similar to [isolated], but it matches based on a regex
   * By default, all completions are `isolated(".*".toRegex()) {}`
   */
  fun isolated(regex: Regex, childBuilder: NestedCompletionsNodeBuilder.() -> Unit)

  /** Constructs a node that allows completions from outside to nest into this node */
  fun open(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit = {})
}

/**
 * Represents a tree structure of how completions can be nested through a schema.
 *
 * If you request completions in a configuration node that has a corresponding [com.jetbrains.jsonSchema.impl.JsonSchemaObject]
 * as well as a corresponding [NestedCompletionsNode], you will see completions for the entire subtree of the [NestedCompletionsNode].
 * (subtrees are not expanded below [com.jetbrains.jsonSchema.impl.nestedCompletions.ChildNode.Isolated] nodes)
 *
 * See tests for details
 */
@ApiStatus.Experimental
class NestedCompletionsNode(val children: List<ChildNode>)

sealed interface ChildNode {
  val node: NestedCompletionsNode

  sealed interface NamedChildNode : ChildNode {
    val name: String
  }

  sealed class Isolated : ChildNode {
    data class RegexNode(val regex: Regex, override val node: NestedCompletionsNode) : Isolated()
    data class NamedNode(override val name: String, override val node: NestedCompletionsNode) : Isolated(), NamedChildNode
  }

  data class OpenNode(override val name: String, override val node: NestedCompletionsNode) : ChildNode, NamedChildNode
}

fun buildNestedCompletionsTree(block: NestedCompletionsNodeBuilder.() -> Unit): NestedCompletionsNode =
  NestedCompletionsNode(
    children = buildList {
      block(
        object : NestedCompletionsNodeBuilder {
          override fun isolated(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit) {
            add(ChildNode.Isolated.NamedNode(name, buildNestedCompletionsTree(childBuilder)))
          }

          override fun isolated(regex: Regex, childBuilder: NestedCompletionsNodeBuilder.() -> Unit) {
            add(ChildNode.Isolated.RegexNode(regex, buildNestedCompletionsTree(childBuilder)))
          }

          override fun open(name: String, childBuilder: NestedCompletionsNodeBuilder.() -> Unit) {
            add(ChildNode.OpenNode(name, buildNestedCompletionsTree(childBuilder)))
          }
        }
      )
    }
  )

internal fun NestedCompletionsNode.merge(other: NestedCompletionsNode): NestedCompletionsNode =
  NestedCompletionsNode(children + other.children)

internal fun NestedCompletionsNode?.navigate(jsonPointer: JsonPointerPosition): NestedCompletionsNode? =
  this?.navigate(0, jsonPointer.toPathItems())

private fun JsonPointerPosition.toPathItems() =
  toJsonPointer()?.takeIf { it != "/" }?.drop(1)?.split("/") ?: emptyList()

private tailrec fun NestedCompletionsNode.navigate(index: Int, steps: List<String>): NestedCompletionsNode? =
  if (index !in steps.indices) this
  else {
    val matchingNodes = children.filter { it.matches(steps[index]) }
    matchingNodes.getPreferredChild()?.node?.navigate(index + 1, steps)
  }

// some schemas provide both named and regex nodes for the same name
// we need to prioritize named options over regex options
private fun Collection<ChildNode>.getPreferredChild(): ChildNode? {
  return firstOrNull { it is ChildNode.NamedChildNode } ?: firstOrNull()
}

private fun ChildNode.matches(name: String): Boolean = when (this) {
  is ChildNode.Isolated.RegexNode -> regex.matches(name)
  is ChildNode.NamedChildNode -> this.name == name
}
