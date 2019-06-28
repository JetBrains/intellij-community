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
package com.intellij.diagnostic.hprof.visitors

import com.intellij.diagnostic.hprof.classstore.ClassDefinition
import com.intellij.diagnostic.hprof.classstore.ClassStore
import com.intellij.diagnostic.hprof.classstore.InstanceField
import com.intellij.diagnostic.hprof.classstore.StaticField
import com.intellij.diagnostic.hprof.parser.*
import gnu.trove.TLongArrayList
import gnu.trove.TLongLongHashMap
import gnu.trove.TLongObjectHashMap
import gnu.trove.TObjectLongHashMap

class CreateClassStoreVisitor : HProfVisitor() {

  private val stringIDToString = TLongObjectHashMap<String?>()
  private val classIDToNameStringID = TLongLongHashMap()

  private val fieldToNameID = TObjectLongHashMap<InstanceField>()
  private val staticFieldToNameID = TObjectLongHashMap<StaticField>()

  private val result = TLongObjectHashMap<ClassDefinition>()
  private var completed = false
  private var namesUpdated = false

  fun getStringIDToStringMap(): TLongObjectHashMap<String?> {
    assert(completed)
    return stringIDToString
  }

  override fun preVisit() {
    disableAll()
    enable(RecordType.LoadClass)
    enable(HeapDumpRecordType.ClassDump)
  }

  override fun postVisit() {
    completed = true
  }

  override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
    stringIDToString.put(classNameStringId, null)
    classIDToNameStringID.put(classObjectId, classNameStringId)
  }

  override fun visitClassDump(
    classId: Long,
    stackTraceSerialNumber: Long,
    superClassId: Long,
    classloaderClassId: Long,
    instanceSize: Long,
    constants: Array<ConstantPoolEntry>,
    staticFields: Array<StaticFieldEntry>,
    instanceFields: Array<InstanceFieldEntry>) {
    val instanceFieldList = mutableListOf<InstanceField>()
    val staticFieldList = mutableListOf<StaticField>()
    var currentOffset = 0
    instanceFields.forEach {
      if (it.type != Type.OBJECT) {
        currentOffset += it.type.size
      }
      else {
        val field = InstanceField("<missingFieldName>", currentOffset)
        instanceFieldList.add(field)
        fieldToNameID.put(field, it.fieldNameStringId)
        stringIDToString.put(it.fieldNameStringId, null)
        currentOffset += visitorContext.idSize
      }
    }
    val constantsArray = TLongArrayList(constants.size)
    constants.filter { it.type == Type.OBJECT }.forEach { constantsArray.add(it.value) }
    val objectStaticFields = staticFields.filter { it.type == Type.OBJECT }
    objectStaticFields.forEach {
      val field = StaticField("<missingFieldName>", it.value)
      staticFieldList.add(field)
      staticFieldToNameID.put(field, it.fieldNameStringId)
      stringIDToString.put(it.fieldNameStringId, null)
    }
    result.put(classId,
               ClassDefinition(
                 "<missingClassName>",
                 classId,
                 superClassId,
                 instanceSize.toInt(),
                 currentOffset,
                 instanceFieldList.toTypedArray(),
                 constantsArray.toNativeArray(),
                 staticFieldList.toTypedArray()
               ))
  }

  fun updateNames() {
    result.transformValues { classDefinition ->
      val className = stringIDToString[classIDToNameStringID[classDefinition.id]]!!.replace('/', '.')
      ClassDefinition(
        className,
        classDefinition.id,
        classDefinition.superClassId,
        classDefinition.instanceSize,
        classDefinition.superClassOffset,
        classDefinition.refInstanceFields.map { fieldObj ->
          val nameId = fieldToNameID[fieldObj]
          InstanceField(
            stringIDToString[nameId]!!,
            fieldObj.offset
          )
        }.toTypedArray(),
        classDefinition.constantFields,
        classDefinition.staticFields.map { fieldObj ->
          val nameId = staticFieldToNameID[fieldObj]
          StaticField(
            stringIDToString[nameId]!!,
            fieldObj.objectId
          )
        }.toTypedArray()
      )
    }
    namesUpdated = true
  }

  fun getClassStore(): ClassStore {
    assert(completed)
    assert(namesUpdated)
    return ClassStore(result)
  }
}