// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.memory.agent.MemoryAgentReferringObjectProvider
import com.intellij.debugger.engine.ReferringObjectsProvider
import com.intellij.openapi.diagnostic.Logger
import com.sun.jdi.ArrayReference
import com.sun.jdi.IntegerValue
import com.sun.jdi.ObjectReference
import com.sun.jdi.Value
import java.util.ArrayList

class GcRootsPathsParser : ResultParser<ReferringObjectsProvider> {
  private companion object {
    val LOG = Logger.getInstance(GcRootsPathsParser::class.java)
  }

  override fun parse(value: Value): ReferringObjectsProvider {
    if (value is ArrayReference) {
      LOG.assertTrue(value.length() == 2, "Array must represent 2 values: objects and backward references")
      val values = parseValues(value.getValue(0))
      val backwardReferences = parseBackwardReferences(value.getValue(1))
      return MemoryAgentReferringObjectProvider(values, backwardReferences)
    }
    throw AssertionError("Incorrect result format: array of arrays is expected")
  }

  private fun parseBackwardReferences(value: Value): List<List<Int>> {
    if (value is ArrayReference) {
      val result = ArrayList<List<Int>>()
      for (item in value.values) {
        if (item !is ArrayReference || "int" == item.type().name()) {
          throw AssertionError("Incorrect result format: int array expected")
        }
        val ints = item.values.map { x -> (x as IntegerValue).value() }
        result.add(ints)
      }

      return result
    }

    throw AssertionError("Incorrect result format: array with nested int arrays expected")
  }

  private fun parseValues(value: Value): List<ObjectReference> {
    if (value is ArrayReference) {
      val result = ArrayList<ObjectReference>()
      for (item in value.values) {
        if (item !is ObjectReference) break
        result.add(item)
      }

      if (result.size != value.length()) {
        throw AssertionError("All values should be object references but some of them are not")
      }

      return result
    }

    throw AssertionError("Incorrect result format: array with object references expected")
  }
}