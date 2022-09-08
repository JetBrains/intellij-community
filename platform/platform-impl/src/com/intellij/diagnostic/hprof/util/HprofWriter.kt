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
package com.intellij.diagnostic.hprof.util

import com.intellij.diagnostic.hprof.parser.*
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.DataOutput
import java.io.DataOutputStream

class HprofWriter(
  private val dos: DataOutputStream,
  private val idSize: Int,
  timestamp: Long
) : Closeable {

  init {
    if (idSize != 4 && idSize != 8) {
      throw IllegalArgumentException("idSize can only be 4 or 8")
    }
    dos.writeNullTerminatedString("JAVA PROFILE 1.0.1")
    dos.writeInt(idSize)
    dos.writeLong(timestamp)
  }

  private val stringToIdMap = Object2LongOpenHashMap<String>()
  private val idToStringMap = Long2ObjectOpenHashMap<String>()
  private var nextStringId = 1L
  private val subtagsBaos = ByteArrayOutputStream()
  private var subtagsStream = DataOutputStream(subtagsBaos)

  override fun close() {
    flushHeapObjects()
    dos.close()
  }

  private fun DataOutput.writeNullTerminatedString(s: String) {
    this.write(s.toByteArray())
    this.write(0)
  }

  fun writeStringInUTF8(id: Long, s: String) {
    val bytes = s.toByteArray()
    writeRecordHeader(RecordType.StringInUTF8, bytes.size + idSize)
    dos.writeId(id)
    dos.write(bytes)
  }

  fun writeLoadClass(serialNumber: Int, classObjectId: Long, stackTraceSerialNumber: Int, classNameId: Long) {
    writeRecordHeader(RecordType.LoadClass, 8 + idSize * 2)
    dos.writeInt(serialNumber)
    dos.writeId(classObjectId)
    dos.writeInt(stackTraceSerialNumber)
    dos.writeId(classNameId)
  }

  fun flushHeapObjects() {
    writeHeapDumpRecords()
  }

  private fun writeHeapDumpRecords() {
    if (subtagsBaos.size() > 0) {
      subtagsStream.close()
      writeRecordHeader(RecordType.HeapDump, subtagsBaos.size())
      subtagsBaos.writeTo(dos)
      subtagsBaos.reset()
      subtagsStream = DataOutputStream(subtagsBaos)
      writeRecordHeader(RecordType.HeapDumpEnd, 0)
    }
  }

  fun writeRootUnknown(id: Long) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.RootUnknown)
      writeId(id)
    }
  }

  fun writeRootJavaFrame(objectId: Long, threadSerialNumber: Int, frameNumber: Int) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.RootJavaFrame)
      writeId(objectId)
      writeInt(threadSerialNumber)
      writeInt(frameNumber)
    }
  }

  fun writeClassDump(
    classObjectId: Long,
    stackTraceSerialNumber: Int,
    superClassObjectId: Long,
    classLoaderObjectId: Long,
    signersObjectId: Long,
    protectionDomainObjectId: Long,
    instanceSize: Int,
    constantPool: Array<ConstantPoolEntry>,
    staticFields: Array<StaticFieldEntry>,
    instanceFields: Array<InstanceFieldEntry>
  ) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.ClassDump)
      writeId(classObjectId)
      writeInt(stackTraceSerialNumber)
      writeId(superClassObjectId)
      writeId(classLoaderObjectId)
      writeId(signersObjectId)
      writeId(protectionDomainObjectId)
      writeId(0)
      writeId(0)
      writeInt(instanceSize)
      writeShort(constantPool.size)
      for (entry in constantPool) {
        writeShort(entry.constantPoolIndex)
        writeByte(entry.type.typeId)
        writeValue(entry.value, entry.type)
      }
      writeShort(staticFields.size)
      for (entry in staticFields) {
        writeId(entry.fieldNameStringId)
        writeByte(entry.type.typeId)
        writeValue(entry.value, entry.type)
      }
      writeShort(instanceFields.size)
      for (entry in instanceFields) {
        writeId(entry.fieldNameStringId)
        writeByte(entry.type.typeId)
      }
    }
  }

  fun writeInstanceDump(
    objectId: Long,
    stackTraceSerialNumber: Int,
    classObjectId: Long,
    bytes: ByteArray
  ) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.InstanceDump)
      writeId(objectId)
      writeInt(stackTraceSerialNumber)
      writeId(classObjectId)
      writeInt(bytes.count())
      write(bytes)
    }
  }

  fun writeObjectArrayDump(
    arrayObjectId: Long,
    stackTraceSerialNumber: Int,
    arrayClassObjectId: Long,
    elementIds: LongArray
  ) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.ObjectArrayDump)
      writeId(arrayObjectId)
      writeInt(stackTraceSerialNumber)
      writeInt(elementIds.count())
      writeId(arrayClassObjectId)
      elementIds.forEach { id ->
        writeId(id)
      }
    }
  }

  fun writePrimitiveArrayDump(
    arrayObjectId: Long,
    stackTraceSerialNumber: Int,
    elementType: Type,
    elements: ByteArray,
    elementsCount: Int
  ) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.PrimitiveArrayDump)
      writeId(arrayObjectId)
      writeInt(stackTraceSerialNumber)
      writeInt(elementsCount)
      assert(elementType != Type.OBJECT)
      writeByte(elementType.typeId)
      assert(elements.size == elementsCount * elementType.size)
      write(elements)
    }
  }

  private fun getOrCreateStringId(s: String): Long {
    val id = stringToIdMap.getLong(s)
    if (id == 0L) {
      val newId = nextStringId++
      writeStringInUTF8(newId, s)
      idToStringMap.put(newId, s)
      stringToIdMap.put(s, newId)
      return newId
    }
    return id
  }


  private fun writeRecordHeader(recordType: RecordType, length: Int) {
    with(dos) {
      writeByte(recordType.value)
      writeInt(0) // timestamp, not supported
      writeInt(length)
    }
  }

  private fun writeHeapDumpRecordHeader(heapDumpRecordType: HeapDumpRecordType) {
    with(subtagsStream) {
      writeByte(heapDumpRecordType.value)
    }
  }

  fun writeRootGlobalJNI(objectId: Long, jniGlobalRefId: Long) {
    with(subtagsStream) {
      writeHeapDumpRecordHeader(HeapDumpRecordType.RootGlobalJNI)
      writeId(objectId)
      writeId(jniGlobalRefId)
    }
  }

  private fun DataOutputStream.writeId(id: Long) {
    when (idSize) {
      4 -> this.writeInt(id.toInt())
      8 -> this.writeLong(id)
      else -> assert(false)
    }
  }

  private fun DataOutputStream.writeValue(value: Long, type: Type) {
    if (type == Type.OBJECT) {
      writeId(value)
    }
    else {
      when (type.size) {
        1 -> writeByte(value.toInt())
        2 -> writeShort(value.toInt())
        4 -> writeInt(value.toInt())
        8 -> writeLong(value)
        else -> assert(false)
      }
    }
  }

  fun writeStackTrace(stackTraceSerialNumber: Int, threadSerialNumber: Long, stackFrameIds: LongArray) {
    with (dos) {
      val length = 3 * 4 + idSize * stackFrameIds.size
      writeRecordHeader(RecordType.StackTrace, length)
      writeInt(stackTraceSerialNumber)
      writeInt(threadSerialNumber.toInt())
      writeInt(stackFrameIds.size)
      for (frameId in stackFrameIds) {
        writeId(frameId)
      }
    }
  }


  fun writeStackFrame(stackFrameId: Long,
                       methodNameStringId: Long,
                       methodSignatureStringId: Long,
                       sourceFilenameStringId: Long,
                       classSerialNumber: Int,
                       lineNumber: Int)
  {
    with (dos) {
      val length = (4 * idSize) + (2 * 4)
      writeRecordHeader(RecordType.StackFrame, length)
      writeId(stackFrameId)
      writeId(methodNameStringId)
      writeId(methodSignatureStringId)
      writeId(sourceFilenameStringId)
      writeInt(classSerialNumber)
      writeInt(lineNumber)
    }
  }
}

