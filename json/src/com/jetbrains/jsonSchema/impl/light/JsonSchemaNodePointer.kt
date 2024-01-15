// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl.light

import com.jetbrains.jsonSchema.impl.JsonSchemaObject

interface JsonSchemaNodePointer<T> {
  val rawSchemaNode: T
}

interface JsonSchemaObjectFactory<T, V> where V : JsonSchemaObject, V : JsonSchemaNodePointer<T> {
  /**
   * @return an instance of schema object backed by physically existing schema node that can be found by a combined json pointer, where
   * the combined pointer is created by concatenation of the current node pointer and childNodeRelativePointer argument.
   */
  fun getChildSchemaObjectByName(parentSchemaObject: V, vararg childNodeRelativePointer: String): JsonSchemaObject?

  /**
   * @return an instance of schema object backed by physically existing schema node that can be found by an absolute json pointer
   */
  fun getSchemaObjectByAbsoluteJsonPointer(jsonPointer: String): JsonSchemaObject?
}

/**
 * An interface to encapsulate all schema traversal methods to be able to replace the implementation without changing an existing API
 */
interface RawJsonSchemaNodeAccessor<T> {
  /**
   * Resolve raw schema node from the given schema root by the given node's json pointer
   */
  fun resolveNode(rootNode: T, absoluteNodeJsonPointer: String): T?

  /**
   * Resolve raw schema node from the given schema node by the given node's name
   */
  fun resolveRelativeNode(node: T, vararg relativeChildPath: String): T?

  fun hasChildNode(node: T, vararg relativeChildPath: String): Boolean

  fun readTextNodeValue(node: T, vararg relativeChildPath: String): String?
  fun readBooleanNodeValue(node: T, vararg relativeChildPath: String): Boolean?
  fun readNumberNodeValue(node: T, vararg relativeChildPath: String): Number?
  fun readUntypedNodeValueAsText(node: T, vararg relativeChildPath: String): String?

  fun readNodeKeys(node: T, vararg relativeChildPath: String): Sequence<String>?

  fun readUntypedNodesCollection(node: T, vararg relativeChildPath: String): Sequence<Any>?
  fun readNodeAsMapEntries(node: T, vararg relativeChildPath: String): Sequence<Pair<String, T>>?
  fun readNodeAsMultiMapEntries(node: T, vararg relativeChildPath: String): Sequence<Pair<String, List<String>>>?
}