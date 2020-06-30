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
import com.intellij.diagnostic.hprof.parser.*
import com.intellij.diagnostic.hprof.util.FileChannelBackedWriteBuffer
import com.intellij.openapi.diagnostic.Logger
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class CreateAuxiliaryFilesVisitor(
  private val auxOffsetsChannel: FileChannel,
  private val auxChannel: FileChannel,
  private val classStore: ClassStore,
  private val parser: HProfEventBasedParser
) : HProfVisitor() {
  private lateinit var offsets: FileChannelBackedWriteBuffer
  private lateinit var aux: FileChannelBackedWriteBuffer

  private var directByteBufferClass: ClassDefinition? = null
  private var directByteBufferCapacityOffset: Int = 0
  private var directByteBufferFdOffset: Int = 0
  private var stringClass: ClassDefinition? = null
  private var stringCoderOffset: Int = -1

  companion object {
    private val LOG = Logger.getInstance(CreateAuxiliaryFilesVisitor::class.java)
  }

  override fun preVisit() {
    disableAll()
    enable(HeapDumpRecordType.ClassDump)
    enable(HeapDumpRecordType.InstanceDump)
    enable(HeapDumpRecordType.ObjectArrayDump)
    enable(HeapDumpRecordType.PrimitiveArrayDump)

    offsets = FileChannelBackedWriteBuffer(auxOffsetsChannel)
    aux = FileChannelBackedWriteBuffer(auxChannel)

    directByteBufferClass = null
    val dbbClass = classStore.getClassIfExists("java.nio.DirectByteBuffer")

    if (dbbClass != null) {
      directByteBufferClass = dbbClass

      directByteBufferCapacityOffset = dbbClass.computeOffsetOfField("capacity", classStore)
      directByteBufferFdOffset = dbbClass.computeOffsetOfField("fd", classStore)

      if (directByteBufferCapacityOffset == -1 || directByteBufferFdOffset == -1) {
        LOG.error("DirectByteBuffer.capacity and/or .fd field is missing.")
      }
    }

    stringClass = classStore.getClassIfExists("java.lang.String")
    stringClass?.let {
      stringCoderOffset = it.computeOffsetOfField("coder", classStore)
    }

    // Map id=0 to 0
    offsets.writeInt(0)
  }

  override fun postVisit() {
    aux.close()
    offsets.close()
  }

  override fun visitPrimitiveArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, numberOfElements: Long, elementType: Type, primitiveArrayData: ByteBuffer) {
    assert(arrayObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == arrayObjectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(classStore.getClassForPrimitiveArray(elementType)!!.id.toInt())

    assert(numberOfElements <= Int.MAX_VALUE) // arrays in java don't support more than Int.MAX_VALUE elements
    aux.writeNonNegativeLEB128Int(numberOfElements.toInt())
    aux.writeBytes(primitiveArrayData)
  }

  override fun visitClassDump(classId: Long,
                              stackTraceSerialNumber: Long,
                              superClassId: Long,
                              classloaderClassId: Long,
                              instanceSize: Long,
                              constants: Array<ConstantPoolEntry>,
                              staticFields: Array<StaticFieldEntry>,
                              instanceFields: Array<InstanceFieldEntry>) {
    assert(classId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == classId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(0) // Special value for class definitions, to differentiate from regular java.lang.Class instances
  }

  override fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {
    assert(arrayObjectId <= Int.MAX_VALUE)
    assert(arrayClassObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == arrayObjectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(arrayClassObjectId.toInt())
    val nonNullElementsCount = objects.count { it != 0L }
    val nullElementsCount = objects.count() - nonNullElementsCount

    // To minimize serialized size, store (nullElementsCount, nonNullElementsCount) pair instead of
    // (objects.count, nonNullElementsCount) pair.

    aux.writeNonNegativeLEB128Int(nullElementsCount)
    aux.writeNonNegativeLEB128Int(nonNullElementsCount)

    objects.forEach {
      if (it == 0L) return@forEach
      assert(it <= Int.MAX_VALUE)
      aux.writeId(it.toInt())
    }
  }

  override fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {
    assert(objectId <= Int.MAX_VALUE)
    assert(classObjectId <= Int.MAX_VALUE)
    assert(offsets.position() / 4 == objectId.toInt())

    offsets.writeInt(aux.position())

    aux.writeId(classObjectId.toInt())

    var classOffset = 0
    val objectClass = classStore[classObjectId]
    run {
      var classDef: ClassDefinition = objectClass
      do {
        classDef.refInstanceFields.forEach {
          val offset = classOffset + it.offset
          val value = bytes.getLong(offset)

          if (value == 0L) {
            aux.writeId(0)
          }
          else {
            // bytes are just raw data. IDs have to be mapped manually.
            val reference = parser.remap(value)
            assert(reference != 0L)
            aux.writeId(reference.toInt())
          }
        }
        classOffset += classDef.superClassOffset

        if (classDef.superClassId == 0L) {
          break
        }
        classDef = classStore[classDef.superClassId]
      }
      while (true)
    }

    // DirectByteBuffer class contains additional field with buffer capacity.
    if (objectClass == directByteBufferClass) {
      if (directByteBufferCapacityOffset == -1 || directByteBufferFdOffset == -1) {
        aux.writeNonNegativeLEB128Int(1)
      }
      else {
        val directByteBufferCapacity = bytes.getInt(directByteBufferCapacityOffset)
        val directByteBufferFd = bytes.getLong(directByteBufferFdOffset)
        if (directByteBufferFd == 0L) {
          // When fd == 0, the buffer is directly allocated in memory.
          aux.writeNonNegativeLEB128Int(directByteBufferCapacity)
        }
        else {
          // File-mapped buffer
          aux.writeNonNegativeLEB128Int(1)
        }
      }
    }
    else if (objectClass == stringClass) {
      if (stringCoderOffset == -1) {
        aux.writeByte(-1)
      }
      else {
        aux.writeByte(bytes.get(stringCoderOffset))
      }
    }
  }

  private fun FileChannelBackedWriteBuffer.writeId(id: Int) {
    // Use variable-length Int for IDs to save space
    this.writeNonNegativeLEB128Int(id)
  }
}

