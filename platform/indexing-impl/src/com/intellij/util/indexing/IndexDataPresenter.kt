// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing

import com.intellij.openapi.util.io.ByteArraySequence
import com.intellij.psi.stubs.*
import com.intellij.util.Base64

object IndexDataPresenter {

  fun <K> getPresentableIndexKey(key: K): String = key.toString()

  fun <V> getPresentableIndexValue(value: V?): String {
    return when (value) {
      null -> "<no value>"
      is SerializedStubTree -> {
        getPresentableSerializedStubTree(value)
      }
      is ByteArraySequence -> {
        Base64.encode(value.toBytes())
      }
      else -> value.toString()
    }
  }

  fun getPresentableSerializedStubTree(value: SerializedStubTree): String =
    buildString {
      appendLine("Stub tree:")
      appendLine(getPresentableStub(value.stub, "  "))

      val stubIndicesValueMap: Map<StubIndexKey<*, *>, Map<Any, StubIdList>> = try {
        value.stubIndicesValueMap
      }
      catch (e: Exception) {
        appendLine("Failed-to-read stub tree forward index: (message = ${e.message}) (exception class = ${e.javaClass.simpleName})")
        return@buildString
      }

      appendLine("Stub tree forward index:")
      for ((stubIndexKey, stubIndexValues) in stubIndicesValueMap.entries.sortedBy { it.key.name }) {
        appendLine("    ${stubIndexKey.name}")
        for ((key, stubIdList) in stubIndexValues.entries.sortedBy { it.key.toString() }) {
          val stubIds = (0 until stubIdList.size()).map { stubIdList[it] }
          appendLine("        $key -> " + stubIds.joinToString())
        }
      }
    }

  fun <K, V> getPresentableKeyValueMap(keyValueMap: Map<K, V>): String {
    if (keyValueMap.isEmpty()) {
      return "{empty map}"
    }
    return buildString {
      for ((key, value) in keyValueMap) {
        appendLine(getPresentableIndexKey(key))
        appendLine(getPresentableIndexValue(value).withIndent("  "))
      }
    }
  }

  fun getPresentableStub(node: Stub): String = getPresentableStub(node, "")

  private fun getPresentableStub(node: Stub, indent: String): String =
    buildString {
      append(indent)
      val stubType = node.stubType
      if (stubType != null) {
        append(stubType.toString()).append(':')
      }
      append(node.toString())
      if (node is ObjectStubBase<*>) {
        append(" (id = ").append(node.stubId).append(")")
      }
      for (child in node.childrenStubs) {
        appendLine(getPresentableStub(child, "$indent  "))
      }
    }

  private fun String.withIndent(indent: String) = lineSequence().joinToString(separator = "\n") { "$indent$it" }
}