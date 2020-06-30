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

import com.intellij.diagnostic.hprof.parser.*
import com.intellij.diagnostic.hprof.util.FileBackedHashMap
import gnu.trove.TLongIntHashMap
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.util.function.LongUnaryOperator

abstract class RemapIDsVisitor : HProfVisitor() {
  private var currentID = 0

  override fun preVisit() {
    disableAll()
    enable(HeapDumpRecordType.ClassDump)
    enable(HeapDumpRecordType.InstanceDump)
    enable(HeapDumpRecordType.PrimitiveArrayDump)
    enable(HeapDumpRecordType.ObjectArrayDump)

    currentID = 1
  }

  override fun visitPrimitiveArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, numberOfElements: Long, elementType: Type, primitiveArrayData: ByteBuffer) {
    addMapping(arrayObjectId, currentID++)
  }

  override fun visitClassDump(classId: Long,
                              stackTraceSerialNumber: Long,
                              superClassId: Long,
                              classloaderClassId: Long,
                              instanceSize: Long,
                              constants: Array<ConstantPoolEntry>,
                              staticFields: Array<StaticFieldEntry>,
                              instanceFields: Array<InstanceFieldEntry>) {
    addMapping(classId, currentID++)
  }

  override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
    addMapping(arrayObjectId, currentID++)
  }

  override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {
    addMapping(objectId, currentID++)
  }

  abstract fun addMapping(oldId: Long, newId: Int)

  abstract fun getRemappingFunction(): LongUnaryOperator

  companion object {
    fun createMemoryBased(): RemapIDsVisitor {
      val map = TLongIntHashMap()
      map.put(0, 0)
      return object : RemapIDsVisitor() {
        override fun addMapping(oldId: Long, newId: Int) {
          map.put(oldId, newId)
        }

        override fun getRemappingFunction(): LongUnaryOperator {
          return LongUnaryOperator { map[it].toLong() }
        }
      }
    }

    fun createFileBased(channel: FileChannel, maxInstanceCount: Long): RemapIDsVisitor {
      val remapIDsMap = FileBackedHashMap.createEmpty(
        channel,
        maxInstanceCount, KEY_SIZE, VALUE_SIZE)
      return object : RemapIDsVisitor() {
        override fun addMapping(oldId: Long, newId: Int) {
          remapIDsMap.put(oldId).putInt(newId)
        }

        override fun getRemappingFunction(): LongUnaryOperator {
          return LongUnaryOperator { operand ->
            if (operand == 0L) 0L else remapIDsMap[operand]!!.int.toLong()
          }
        }
      }
    }

    fun isSupported(instanceCount: Long): Boolean {
      return FileBackedHashMap.isSupported(instanceCount, KEY_SIZE, VALUE_SIZE)
    }

    private const val KEY_SIZE = 8
    private const val VALUE_SIZE = 4
  }
}