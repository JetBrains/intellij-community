// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.memory.agent.UnexpectedValueFormatException
import com.sun.jdi.*
import java.util.*

object BooleanParser : ResultParser<Boolean> {
  override fun parse(value: Value): Boolean {
    return (value as? BooleanValue)?.value() ?: false
  }
}

object LongValueParser : ResultParser<Long> {
  override fun parse(value: Value): Long {
    if (value is PrimitiveValue) {
      return value.longValue()
    }

    throw UnexpectedValueFormatException("Primitive value is expected")
  }
}

object ObjectReferencesParser : ResultParser<List<ObjectReference>> {
  override fun parse(value: Value): List<ObjectReference> {
    if (value is ArrayReference) {
      val result = ArrayList<ObjectReference>()
      for (item in value.values) {
        if (item !is ObjectReference) break
        result.add(item)
      }

      if (result.size != value.length()) {
        throw UnexpectedValueFormatException(
          "All values should be object references but some of them are not")
      }

      return result
    }

    throw UnexpectedValueFormatException("Array with object references expected")
  }
}

object BackwardReferencesParser : ResultParser<List<List<Int>>> {
  override fun parse(value: Value): List<List<Int>> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array with nested arrays expected")

    val result = ArrayList<List<Int>>()
    for (linksInfo in value.values) {
      if (linksInfo !is ArrayReference) throw UnexpectedValueFormatException("Object references information should be represented by array")
      val indices = IntArrayParser.parse(linksInfo.getValue(0))
        .distinct() // drop duplicates
        .filter { it != -1 } // drop gc roots
      // TODO: parse kinds and infos as well

      result.add(indices)
    }

    return result
  }
}

object IntArrayParser : ResultParser<List<Int>> {
  override fun parse(value: Value): List<Int> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    val items = value.values
    if (items.isEmpty()) return emptyList()
    if (items[0] !is IntegerValue) throw UnexpectedValueFormatException("array elements should be integers")
    return items.map { it as IntegerValue }.map(IntegerValue::value)
  }
}

object LongArrayParser : ResultParser<List<Long>> {
  override fun parse(value: Value): List<Long> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    return value.values.map(LongValueParser::parse)
  }
}