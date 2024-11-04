@file:ApiStatus.Internal

// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.fasterxml.jackson.databind.node.MissingNode
import com.intellij.openapi.diagnostic.fileLogger
import com.jetbrains.jsonSchema.impl.light.nodes.JacksonSchemaNodeAccessor
import com.jetbrains.jsonSchema.impl.light.nodes.JsonSchemaObjectBackedByJacksonBase
import org.jetbrains.annotations.ApiStatus

@JvmName("getChildAsText")
fun getChildAsText(node: JsonSchemaObject, vararg relativeChildPath: String): String? {
  return when (node) {
    is JsonSchemaObjectBackedByJacksonBase -> {
      val rawChildNode = relativeChildPath.asSequence().fold(node.rawSchemaNode) { currentNode, childName ->
        JacksonSchemaNodeAccessor.resolveRelativeNode(currentNode, childName) ?: return@fold MissingNode.getInstance()
      }
      return JacksonSchemaNodeAccessor.readTextNodeValue(rawChildNode)
    }
    else -> {
      fileLogger().warn("JSON schema traverser does not provide support for ${node::class.java.simpleName}")
      null
    }
  }
}