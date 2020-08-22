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
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.longs.LongArrayList

internal class CreateClassStoreVisitor(private val stringIdMap: Long2ObjectMap<String>) : HProfVisitor() {
  private val classIDToNameStringID = Long2LongOpenHashMap()

  private val result = Long2ObjectOpenHashMap<ClassDefinition>()
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
    val constantsArray = LongArrayList(constants.size)
    constants.asSequence()
      .filter { it.type == Type.OBJECT }
      .forEach { constantsArray.add(it.value) }
    val objectStaticFieldList = staticFields.asSequence()
      .filter { it.type == Type.OBJECT }
      .map { StaticField(stringIdMap[it.fieldNameStringId], it.value) }
    val primitiveStaticFieldList = staticFields.asSequence()
      .filter { it.type != Type.OBJECT }
      .map { StaticField(stringIdMap[it.fieldNameStringId], it.value) }

    val classDefinition = ClassDefinition(
      name = stringIdMap.get(classIDToNameStringID.get(classId)).replace('/', '.'),
      id = classId,
      superClassId = superClassId,
      classLoaderId = classloaderClassId,
      instanceSize = instanceSize.toInt(),
      superClassOffset = currentOffset,
      refInstanceFields = refInstanceFields.toTypedArray(),
      primitiveInstanceFields = primitiveInstanceFields.toTypedArray(),
      constantFields = constantsArray.toLongArray(),
      objectStaticFields = objectStaticFieldList.toList().toTypedArray(),
      primitiveStaticFields = primitiveStaticFieldList.toList().toTypedArray()
    )
    result.put(classId, classDefinition)
  }

  fun getClassStore(): ClassStore {
    assert(completed)
    return ClassStore(result)
  }
}