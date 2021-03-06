// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent.parsers

import com.intellij.debugger.engine.ReferringObject
import com.intellij.debugger.memory.agent.*
import com.intellij.openapi.util.Pair
import com.sun.jdi.*
import java.util.*
import kotlin.collections.ArrayList

object StringParser : ResultParser<String> {
  override fun parse(value: Value): String {
    if (value is StringReference) {
      return value.value()
    }

    throw UnexpectedValueFormatException("String value is expected")
  }
}

object BooleanParser : ResultParser<Boolean> {
  override fun parse(value: Value): Boolean {
    if (value is BooleanValue) {
      return value.value()
    }

    throw UnexpectedValueFormatException("Boolean value is expected")
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
    if (value.length() != 3) throw UnexpectedValueFormatException(
      "Array must represent 3 values: objects, backward references and weak/soft reachability flags")

    val objects = ObjectReferencesParser.parse(value.getValue(0))
    val weakSoftReachable = BooleanArrayParser.parse(value.getValue(2))
    val backwardReferences = parseLinksInfos(objects, weakSoftReachable, value.getValue(1))
    return ReferringObjectsInfo(objects, backwardReferences)
  }

  private fun parseLinksInfos(
    objects: List<ObjectReference>,
    weakSoftReachable: List<Boolean>,
    value: Value): List<List<ReferringObject>> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array of arrays is expected")

    val result = ArrayList<List<ReferringObject>>()
    for (linksInfo in value.values) {
      if (linksInfo !is ArrayReference) throw UnexpectedValueFormatException("Object references information should be represented by array")

      val indices = IntArrayParser.parse(linksInfo.getValue(0))
      val kinds = IntArrayParser.parse(linksInfo.getValue(1))
      val infos = linksInfo.getValue(2) as? ArrayReference ?:
                  throw UnexpectedValueFormatException("Object references information should be represented by array")

      val distinctIndices = mutableSetOf<Int>()
      val referenceInfos =  LinkedList<ReferringObject>()
      val rootReferenceKinds = mutableListOf<MemoryAgentReferenceKind>()
      for ((i, index) in indices.withIndex()) {
        if (index == -1) {
          rootReferenceKinds.add(MemoryAgentReferenceKind.valueOf(kinds[i]))
        } else if (!distinctIndices.contains(index)) {
          distinctIndices.add(index)
          referenceInfos.add(
            MemoryAgentReferringObjectCreator.createReferringObject(
              objects[index],
              MemoryAgentReferenceKind.valueOf(kinds[i]),
              weakSoftReachable[index],
              infos.getValue(i)
            )
          )
        }
      }

      if (rootReferenceKinds.isNotEmpty()) {
        referenceInfos.add(0, CompoundRootReferringObject(rootReferenceKinds.toTypedArray()))
      }
      result.add(referenceInfos)
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

object ShallowAndRetainedSizeParser : ResultParser<Pair<List<Long>, List<Long>>> {
  override fun parse(value: Value): Pair<List<Long>, List<Long>> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    if (value.length() < 2) throw UnexpectedValueFormatException("Two arrays expected")
    return Pair(
      LongArrayParser.parse(value.getValue(0)),
      LongArrayParser.parse(value.getValue(1))
    )
  }
}

object SizeAndHeldObjectsParser : ResultParser<Pair<Array<Long>, Array<ObjectReference>>> {
  override fun parse(value: Value): Pair<Array<Long>, Array<ObjectReference>> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    if (value.length() < 2) throw UnexpectedValueFormatException("array of longs and array of objects expected")
    return Pair(
      LongArrayParser.parse(value.getValue(0)).toTypedArray(),
      ObjectReferencesParser.parse(value.getValue(1)).toTypedArray()
    )
  }
}

object ErrorCodeParser : ResultParser<Pair<MemoryAgentActionResult.ErrorCode, Value>> {
  override fun parse(value: Value): Pair<MemoryAgentActionResult.ErrorCode, Value> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    return Pair(
      MemoryAgentActionResult.ErrorCode.valueOf(
        IntArrayParser.parse(value.getValue(0))[0]
      ),
      value.getValue(1)
    )
  }
}

object BooleanArrayParser : ResultParser<List<Boolean>> {
  override fun parse(value: Value): List<Boolean> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    return value.values.map(BooleanParser::parse)
  }
}

object StringArrayParser : ResultParser<List<String?>> {
  override fun parse(value: Value): List<String?> {
    if (value !is ArrayReference) throw UnexpectedValueFormatException("Array expected")
    return value.values.map { it?.let { StringParser.parse(it) } }
  }
}

object MemoryAgentReferringObjectCreator {
  fun createRootReferringObject(
    kind: MemoryAgentReferenceKind,
    value: Value?): GCRootReferringObject {
    return if (value == null) GCRootReferringObject(kind) else
      when (kind) {
        MemoryAgentReferenceKind.STACK_LOCAL -> {
          if (value !is ArrayReference) return GCRootReferringObject(kind)
          val methodName = StringArrayParser.parse(value.getValue(1))[0] ?: return GCRootReferringObject(kind)
          val longs = LongArrayParser.parse(value.getValue(0))
          StackLocalReferringObject(kind, methodName, longs[0], longs[1])
        }
        MemoryAgentReferenceKind.JNI_LOCAL -> {
          if (value !is ArrayReference) return GCRootReferringObject(kind)
          val longs = LongArrayParser.parse(value.getValue(0))
          JNILocalReferringObject(kind, longs[0], longs[1])
        }
        else -> GCRootReferringObject(kind)
      }
  }

  fun createReferringObject(
    referrer: ObjectReference,
    kind: MemoryAgentReferenceKind,
    isWeakSoftReachable: Boolean,
    value: Value?): MemoryAgentReferringObject {
    return if (value == null) MemoryAgentKindReferringObject(referrer, isWeakSoftReachable, kind) else
      when (kind) {
        MemoryAgentReferenceKind.FIELD,
        MemoryAgentReferenceKind.STATIC_FIELD -> {
          val field = getFieldByJVMTIFieldIndex(referrer, IntArrayParser.parse(value)[0]) ?:
                      return MemoryAgentKindReferringObject(referrer, isWeakSoftReachable, kind)
          MemoryAgentFieldReferringObject(referrer, isWeakSoftReachable, field)
        }
        MemoryAgentReferenceKind.CONSTANT_POOL ->
          MemoryAgentConstantPoolReferringObject(referrer, IntArrayParser.parse(value)[0])
        MemoryAgentReferenceKind.ARRAY_ELEMENT ->
          MemoryAgentArrayReferringObject(referrer as ArrayReference, isWeakSoftReachable, IntArrayParser.parse(value)[0])
        MemoryAgentReferenceKind.TRUNCATE ->
          MemoryAgentTruncatedReferringObject(referrer, isWeakSoftReachable, IntArrayParser.parse(value)[0])
        else -> MemoryAgentKindReferringObject(referrer, isWeakSoftReachable, kind)
      }
  }

  /***
   * For a detailed algorithm description see
   * https://docs.oracle.com/javase/8/docs/platform/jvmti/jvmti.html#jvmtiHeapReferenceInfoField
   */
  private fun getFieldByJVMTIFieldIndex(reference: ObjectReference, index: Int): Field? {
    if (index < 0) {
      return null
    }

    val allFields = reference.referenceType().allFields()
    val it: ListIterator<Field> = allFields.listIterator(allFields.size)
    var currIndex = index
    var declaringType: ReferenceType? = null
    while (it.hasPrevious()) {
      val field = it.previous()
      if (field.declaringType() != declaringType) {
        declaringType = field.declaringType()
        val fields = declaringType.fields()
        if (currIndex < fields.size) {
          return fields[currIndex]
        }
        currIndex -= fields.size
      }
    }

    return null
  }
}
