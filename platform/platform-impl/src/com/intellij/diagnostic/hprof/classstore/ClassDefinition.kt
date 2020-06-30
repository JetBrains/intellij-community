/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diagnostic.hprof.classstore

import com.intellij.diagnostic.hprof.parser.Type
import java.util.function.LongUnaryOperator

class ClassDefinition(val name: String,
                      val id: Long,
                      val superClassId: Long,
                      val classLoaderId: Long,
                      val instanceSize: Int,
                      val superClassOffset: Int,
                      val refInstanceFields: Array<InstanceField>,
                      private val primitiveInstanceFields: Array<InstanceField>,
                      val constantFields: LongArray,
                      val staticFields: Array<StaticField>) {

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as ClassDefinition

    if (id != other.id) return false

    if (id == 0L && other.id == 0L) {
      return name == other.name
    }

    return true
  }

  val prettyName: String
    get() = computePrettyName(name)

  companion object {
    val OBJECT_PREAMBLE_SIZE = 8
    val ARRAY_PREAMBLE_SIZE = 12

    fun computePrettyName(name: String): String {
      if (!name.startsWith('['))
        return name
      var arraySymbolsCount = 0
      while (name.length > arraySymbolsCount && name[arraySymbolsCount] == '[')
        arraySymbolsCount++
      if (name.length <= arraySymbolsCount) {
        // Malformed name
        return name
      }
      val arrayType: Char = name[arraySymbolsCount]
      val arrayString: String = "[]".repeat(arraySymbolsCount)
      return when (arrayType) {
        'B' -> "byte$arrayString"
        'C' -> "char$arrayString"
        'D' -> "double$arrayString"
        'F' -> "float$arrayString"
        'I' -> "int$arrayString"
        'J' -> "long$arrayString"
        'L' -> "${name.substring(arraySymbolsCount + 1, name.length - 1)}$arrayString"
        'S' -> "short$arrayString"
        'Z' -> "boolean$arrayString"
        else -> name
      }
    }

    val CLASS_FIELD = InstanceField("<class>", -1, Type.OBJECT)
  }

  fun getSuperClass(classStore: ClassStore): ClassDefinition? {
    return when (superClassId) {
      0L -> null
      else -> classStore[superClassId.toInt()]
    }
  }

  override fun hashCode(): Int = id.hashCode()

  fun isArray(): Boolean = name[0] == '['

  fun isPrimitiveArray(): Boolean = isArray() && name.length == 2

  fun copyWithRemappedIDs(remappingFunction: LongUnaryOperator): ClassDefinition {
    fun map(id: Long): Long = remappingFunction.applyAsLong(id)
    val newConstantFields = LongArray(constantFields.size) {
      map(constantFields[it])
    }
    val newStaticFields = Array(staticFields.size) {
      val oldStaticField = staticFields[it]
      StaticField(oldStaticField.name, map(oldStaticField.objectId))
    }
    return ClassDefinition(
      name, map(id), map(superClassId), map(classLoaderId), instanceSize, superClassOffset,
      refInstanceFields, primitiveInstanceFields, newConstantFields, newStaticFields
    )
  }

  fun allRefFieldNames(classStore: ClassStore): List<String> {
    val result = mutableListOf<String>()
    var currentClass = this
    do {
      result.addAll(currentClass.refInstanceFields.map { it.name })
      currentClass = currentClass.getSuperClass(classStore) ?: break
    }
    while (true)
    return result
  }

  fun getRefField(classStore: ClassStore, index: Int): InstanceField {
    var currentIndex = index
    var currentClass = this
    do {
      val size = currentClass.refInstanceFields.size
      if (currentIndex < size) {
        return currentClass.refInstanceFields[currentIndex]
      }
      currentIndex -= size
      currentClass = currentClass.getSuperClass(classStore) ?: break
    }
    while (true)
    if (currentIndex == 0) {
      return CLASS_FIELD
    }
    throw IndexOutOfBoundsException("$index on class $name")
  }

  fun getClassFieldName(index: Int): String {
    if (index in constantFields.indices) {
      return "<constant>"
    }
    if (index in constantFields.size until constantFields.size + staticFields.size) {
      return staticFields[index - constantFields.size].name
    }
    if (index == constantFields.size + staticFields.size) {
      return "<loader>"
    }
    throw IndexOutOfBoundsException("$index on class $name")
  }

  /**
   * Computes offset of a given field in the class (including superclasses)
   * @return Offset of the field, or -1 if the field doesn't exist.
   */
  fun computeOffsetOfField(fieldName: String, classStore: ClassStore): Int {
    var classOffset = 0
    var currentClass = this
    do {
      var field = currentClass.refInstanceFields.find { it.name == fieldName }
      if (field == null) {
        field = currentClass.primitiveInstanceFields.find { it.name == fieldName }
      }
      if (field != null) {
        return classOffset + field.offset
      }
      classOffset += currentClass.superClassOffset
      currentClass = currentClass.getSuperClass(classStore) ?: return -1
    }
    while (true)
  }

  fun copyWithName(newName: String): ClassDefinition {
    return ClassDefinition(newName, id, superClassId, classLoaderId, instanceSize, superClassOffset, refInstanceFields, primitiveInstanceFields,
                           constantFields, staticFields)
  }
}


