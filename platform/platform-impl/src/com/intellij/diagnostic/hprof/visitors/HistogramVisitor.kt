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
import com.intellij.diagnostic.hprof.histogram.Histogram
import com.intellij.diagnostic.hprof.histogram.HistogramEntry
import com.intellij.diagnostic.hprof.parser.*
import java.nio.ByteBuffer

class HistogramVisitor(private val classStore: ClassStore) : HProfVisitor() {
  private var completed = false

  private var instanceCount = 0L

  private var classToHistogramEntryInternal = HashMap<ClassDefinition, InternalHistogramEntry>()

  override fun preVisit() {
    assert(!completed)
    disableAll()
    enable(HeapDumpRecordType.InstanceDump)
    enable(HeapDumpRecordType.ObjectArrayDump)
    enable(HeapDumpRecordType.PrimitiveArrayDump)
    enable(HeapDumpRecordType.ClassDump)
  }

  override fun visitPrimitiveArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, numberOfElements: Long, elementType: Type, primitiveArrayData: ByteBuffer) {
    instanceCount++
    val classDefinition = classStore.getClassForPrimitiveArray(elementType)!!
    classToHistogramEntryInternal.getOrPut(classDefinition) {
      InternalHistogramEntry(classDefinition)
    }.addInstance(numberOfElements * elementType.size + ClassDefinition.ARRAY_PREAMBLE_SIZE)
  }

  override fun visitClassDump(classId: Long,
                              stackTraceSerialNumber: Long,
                              superClassId: Long,
                              classloaderClassId: Long,
                              instanceSize: Long,
                              constants: Array<ConstantPoolEntry>,
                              staticFields: Array<StaticFieldEntry>,
                              instanceFields: Array<InstanceFieldEntry>) {
    instanceCount++
    val classDefinition = classStore.classClass
    classToHistogramEntryInternal.getOrPut(classDefinition) {
      InternalHistogramEntry(classDefinition)
    }.addInstance(classDefinition.instanceSize.toLong() + ClassDefinition.OBJECT_PREAMBLE_SIZE)
  }

  override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
    instanceCount++
    val classDefinition = classStore[arrayClassObjectId]
    classToHistogramEntryInternal.getOrPut(classDefinition) {
      InternalHistogramEntry(classDefinition)
    }.addInstance(objects.size.toLong() * visitorContext.idSize + ClassDefinition.ARRAY_PREAMBLE_SIZE)
  }

  override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {
    instanceCount++
    val classDefinition = classStore[classObjectId]
    classToHistogramEntryInternal.getOrPut(classDefinition) {
      InternalHistogramEntry(classDefinition)
    }.addInstance(classDefinition.instanceSize.toLong() + ClassDefinition.OBJECT_PREAMBLE_SIZE)
  }

  override fun postVisit() {
    completed = true
  }

  fun createHistogram(): Histogram {
    assert(completed)
    val result = ArrayList<HistogramEntry>(classToHistogramEntryInternal.size)

    classToHistogramEntryInternal.forEach { (_, internalEntry) ->
      result.add(internalEntry.asHistogramEntry())
    }
    result.sortByDescending { e -> e.totalInstances }
    return Histogram(result, instanceCount)
  }

  class InternalHistogramEntry(private val classDefinition: ClassDefinition) {

    private var totalInstances = 0L
    private var totalBytes = 0L

    fun addInstance(sizeInBytes: Long) {
      totalInstances++
      totalBytes += sizeInBytes
    }

    fun asHistogramEntry(): HistogramEntry {
      return HistogramEntry(classDefinition, totalInstances, totalBytes)
    }
  }
}
