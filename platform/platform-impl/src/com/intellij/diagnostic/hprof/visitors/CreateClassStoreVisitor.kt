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

class CreateClassStoreVisitor(private val stringIdMap: TLongObjectHashMap<String>) : HProfVisitor() {

  private val classIDToNameStringID = TLongLongHashMap()

  private val result = TLongObjectHashMap<ClassDefinition>()
  private var completed = false

  override fun preVisit() {
    disableAll()
    enable(RecordType.LoadClass)
    enable(HeapDumpRecordType.ClassDump)
    classIDToNameStringID.clear()
  }

  override fun postVisit() {
    completed = true
  }

  override fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {
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
    val refInstanceFields = mutableListOf<InstanceField>()
    val primitiveInstanceFields = mutableListOf<InstanceField>()
    val staticFieldList = mutableListOf<StaticField>()
    var currentOffset = 0
    instanceFields.forEach {
      val fieldName = stringIdMap[it.fieldNameStringId]
      val field = InstanceField(fieldName, currentOffset, it.type)
      if (it.type != Type.OBJECT) {
        primitiveInstanceFields.add(field)
        currentOffset += it.type.size
      }
      else {
        refInstanceFields.add(field)
        currentOffset += visitorContext.idSize
      }
    }
    val constantsArray = TLongArrayList(constants.size)
    constants.filter { it.type == Type.OBJECT }.forEach { constantsArray.add(it.value) }
    val objectStaticFields = staticFields.filter { it.type == Type.OBJECT }
    objectStaticFields.forEach {
      val field = StaticField(stringIdMap[it.fieldNameStringId], it.value)
      staticFieldList.add(field)
    }
    result.put(classId,
               ClassDefinition(
                 stringIdMap[classIDToNameStringID[classId]].replace('/', '.'),
                 classId,
                 superClassId,
                 classloaderClassId,
                 instanceSize.toInt(),
                 currentOffset,
                 refInstanceFields.toTypedArray(),
                 primitiveInstanceFields.toTypedArray(),
                 constantsArray.toNativeArray(),
                 staticFieldList.toTypedArray()
               ))
  }

  fun getClassStore(): ClassStore {
    assert(completed)
    return ClassStore(result)
  }
}