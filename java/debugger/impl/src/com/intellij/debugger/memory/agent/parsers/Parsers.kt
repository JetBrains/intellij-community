// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.memory.agent.*
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

object ObjectsReferencesInfoParser : ResultParser<ReferringObjectsInfo> {
  override fun parse(value: Value): ReferringObjectsInfo {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array of arrays is expected")
    if (value.length() != 2) throw UnexpectedValueFormatException("Array must represent 2 values: objects and backward references")

    val objects = ObjectReferencesParser.parse(value.getValue(0))
    val backwardReferences = parseLinksInfos(objects, value.getValue(1))
    return ReferringObjectsInfo(objects, backwardReferences)
  }

  private fun parseLinksInfos(
    objects: List<ObjectReference>,
    value: Value): List<List<MemoryAgentReferenceInfo>> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array of arrays is expected")

    val result = ArrayList<List<MemoryAgentReferenceInfo>>()
    for (linksInfo in value.values) {
      if (linksInfo !is ArrayReference) throw UnexpectedValueFormatException("Object references information should be represented by array")

      val indices = IntArrayParser.parse(linksInfo.getValue(0))
      val kinds = IntArrayParser.parse(linksInfo.getValue(1))
      val infos = linksInfo.getValue(2) as? ArrayReference ?:
                  throw UnexpectedValueFormatException("Object references information should be represented by array")
      val referenceInfo = indices
        .filter { it != -1 }
        .distinct()
        .withIndex()
        .map {
          createReferenceInfo(
            objects[it.value],
            MemoryAgentReferenceInfo.ReferenceKind.valueOf(kinds[it.index]),
            infos.getValue(it.index)
          )
        }

      result.add(referenceInfo)
    }

    return result
  }

  private fun createReferenceInfo(
    referrer: ObjectReference,
    kind: MemoryAgentReferenceInfo.ReferenceKind,
    value: Value?): MemoryAgentReferenceInfo {
    return if (value == null) SimpleReferenceInfo(referrer, kind) else
      when (kind) {
      MemoryAgentReferenceInfo.ReferenceKind.FIELD,
      MemoryAgentReferenceInfo.ReferenceKind.STATIC_FIELD ->
        ReferenceInfoWithIndex(referrer, kind, IntArrayParser.parse(value)[0])
      else -> SimpleReferenceInfo(referrer, kind)
    }
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
