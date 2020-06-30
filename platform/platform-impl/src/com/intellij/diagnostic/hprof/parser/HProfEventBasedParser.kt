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
package com.intellij.diagnostic.hprof.parser

import com.intellij.diagnostic.hprof.util.HProfReadBuffer
import com.intellij.diagnostic.hprof.util.HProfReadBufferSlidingWindow
import com.google.common.base.Stopwatch
import com.intellij.openapi.diagnostic.Logger
import java.io.EOFException
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.Charset
import java.util.function.LongUnaryOperator

class HProfEventBasedParser(fileChannel: FileChannel) : AutoCloseable {
  companion object {
    private val LOG = Logger.getInstance(HProfEventBasedParser::class.java)
  }

  var idSize: Int = 0
    private set

  private var reparsePosition: Long = 0
  private var remapFunction: LongUnaryOperator? = null
  val buffer: HProfReadBuffer

  private var heapRecordPosition: Long = 0

  init {
    buffer = HProfReadBufferSlidingWindow(fileChannel, this)
    initialParse()
  }

  override fun close() {
    buffer.close()
  }

  fun setIdRemappingFunction(remapFunction: LongUnaryOperator) {
    this.remapFunction = remapFunction
  }

  private fun initialParse() {
    buffer.position(0)
    verifyFormat(readNullTerminatedString())
    idSize = readInt()
    buffer.idSize = idSize
    readLong() // ignore timestamp

    reparsePosition = buffer.position()
  }

  fun accept(visitor: HProfVisitor, description: String?) {
    val stopwatch = Stopwatch.createStarted()
    buffer.position(reparsePosition)
    visitor.visitorContext = object : VisitorContext {
      override val currentHeapRecordOffset: Long
        get() {
          return heapRecordPosition
        }

      override val idSize: Int
        get() {
          return this@HProfEventBasedParser.idSize
        }
    }
    visitor.preVisit()

    while (!buffer.isEof()) {
      val tag = readUnsignedByte()
      readInt() // Ignored: timestamp
      val length = readUnsignedInt()

      val recordType = RecordType.fromInt(tag)
      if (!visitor.isEnabled(recordType)) {
        skip(length)
        continue
      }
      when (recordType) {
        RecordType.StringInUTF8 -> visitor.visitStringInUTF8(readRawId(), readNonNullTerminatedString(length - idSize))
        RecordType.LoadClass -> visitor.visitLoadClass(readUnsignedInt(), readId(), readUnsignedInt(), readRawId())
        RecordType.UnloadClass -> visitor.visitUnloadClass(readUnsignedInt())
        RecordType.HeapDumpSegment,
        RecordType.HeapDump -> acceptHeapDumpSegment(visitor, length)
        RecordType.HeapDumpEnd -> visitor.visitHeapDumpEnd()
        RecordType.HeapSummary -> visitor.visitHeapSummary(
          readUnsignedInt(), readUnsignedInt(), readLong(), readLong())
        RecordType.AllocSites -> {
          visitor.visitAllocSites()
          skip(length)
        }
        RecordType.EndThread -> visitor.visitEndThread(readUnsignedInt())
        RecordType.StackFrame -> {
          visitor.visitStackFrame(readRawId(), readRawId(), readRawId(), readRawId(), readUnsignedInt(), readInt())
        }
        RecordType.StackTrace -> {
          val stackTraceSerialNumber = readUnsignedInt()
          val threadSerialNumber = readUnsignedInt()
          val numberOfFrames = readInt()
          val frameIds = LongArray(numberOfFrames) {
            readRawId()
          }
          visitor.visitStackTrace(stackTraceSerialNumber, threadSerialNumber, numberOfFrames, frameIds)
        }
        RecordType.CPUSamples -> {
          visitor.visitCPUSamples()
          skip(length)
        }
        RecordType.StartThread -> {
          visitor.visitStartThread()
          skip(length)
        }
        RecordType.ControlSettings -> {
          visitor.visitControlSettings()
          skip(length)
        }
        else -> throw RuntimeException("Invalid format.")
      }
    }
    visitor.postVisit()
    LOG.info("HProfEventBasedParser${if (description != null) " - $description" else ""}: $stopwatch")
  }

  private fun skip(count: Long) {
    buffer.position(buffer.position() + count)
  }

  private fun acceptHeapDumpSegment(visitor: HProfVisitor, length: Long) {
    visitor.visitHeapDump()
    val segmentEndPosition = buffer.position() + length
    var currentPosition = buffer.position()
    while (currentPosition < segmentEndPosition) {
      val type = readUnsignedByte()

      val heapDumpRecordType = HeapDumpRecordType.fromInt(type)
      if (visitor.isEnabled(heapDumpRecordType)) {
        saveHeapRecordPosition(currentPosition)
        acceptHeapDumpRecord(heapDumpRecordType, visitor)
      }
      else {
        skipHeapDumpRecord(heapDumpRecordType)
      }
      currentPosition = buffer.position()
    }
  }

  fun acceptHeapDumpRecord(heapDumpRecordType: HeapDumpRecordType, visitor: HProfVisitor) {
    when (heapDumpRecordType) {
      HeapDumpRecordType.RootUnknown -> visitor.visitRootUnknown(readId())
      HeapDumpRecordType.RootGlobalJNI -> visitor.visitRootGlobalJNI(readId(), readRawId())
      HeapDumpRecordType.RootLocalJNI -> visitor.visitRootLocalJNI(readId(), readUnsignedInt(), readUnsignedInt())
      HeapDumpRecordType.RootJavaFrame -> visitor.visitRootJavaFrame(readId(), readUnsignedInt(), readUnsignedInt())
      HeapDumpRecordType.RootNativeStack -> visitor.visitRootNativeStack(readId(), readUnsignedInt())
      HeapDumpRecordType.RootStickyClass -> visitor.visitRootStickyClass(readId())
      HeapDumpRecordType.RootThreadBlock -> visitor.visitRootThreadBlock(readId(), readUnsignedInt())
      HeapDumpRecordType.RootMonitorUsed -> visitor.visitRootMonitorUsed(readId())
      HeapDumpRecordType.RootThreadObject -> visitor.visitRootThreadObject(readId(), readUnsignedInt(), readUnsignedInt())
      HeapDumpRecordType.ClassDump -> acceptClassDump(visitor)
      HeapDumpRecordType.InstanceDump -> acceptInstanceDump(visitor)
      HeapDumpRecordType.ObjectArrayDump -> acceptObjectArrayDump(visitor)
      HeapDumpRecordType.PrimitiveArrayDump -> acceptPrimitiveArrayDump(visitor)
      else -> throw IOException("Unknown heap dump record type: $heapDumpRecordType")
    }
  }

  private fun skipHeapDumpRecord(heapDumpRecordType: HeapDumpRecordType) {
    when (heapDumpRecordType) {
      HeapDumpRecordType.RootUnknown -> skip(idSize.toLong())
      HeapDumpRecordType.RootGlobalJNI -> skip((idSize * 2).toLong())
      HeapDumpRecordType.RootLocalJNI -> skip((idSize + 2 * 4).toLong())
      HeapDumpRecordType.RootJavaFrame -> skip((idSize + 2 * 4).toLong())
      HeapDumpRecordType.RootNativeStack -> skip((idSize + 1 * 4).toLong())
      HeapDumpRecordType.RootStickyClass -> skip(idSize.toLong())
      HeapDumpRecordType.RootThreadBlock -> skip((idSize + 1 * 4).toLong())
      HeapDumpRecordType.RootMonitorUsed -> skip(idSize.toLong())
      HeapDumpRecordType.RootThreadObject -> skip((idSize + 2 * 4).toLong())
      HeapDumpRecordType.ClassDump -> {
        skip((7 * idSize + 2 * 4).toLong())
        var toSkip = readUnsignedShort()
        for (i in 0 until toSkip) {
          skip(2)
          readTypeSizeValue(Type.getType(readUnsignedByte()))
        }
        toSkip = readUnsignedShort()
        for (i in 0 until toSkip) {
          skip(idSize.toLong())
          readTypeSizeValue(Type.getType(readUnsignedByte()))
        }
        toSkip = readUnsignedShort()
        skip((toSkip * (idSize + 1)).toLong())
      }
      HeapDumpRecordType.InstanceDump -> {
        skip((idSize * 2 + 4).toLong())
        val remainingBytes = readUnsignedInt()
        skip(remainingBytes)
      }
      HeapDumpRecordType.ObjectArrayDump -> {
        skip((idSize + 4).toLong())
        val objectArraySize = readUnsignedInt()
        skip(idSize * (objectArraySize + 1))
      }
      HeapDumpRecordType.PrimitiveArrayDump -> {
        skip((idSize + 4).toLong())
        val primitiveArraySize = readUnsignedInt()
        val elementTypeID = readByte()
        skip(primitiveArraySize * Type.getType(elementTypeID.toInt()).size)
      }
      else -> throw IOException("Unknown heap dump record type: $heapDumpRecordType")
    }
  }

  private fun saveHeapRecordPosition(position: Long) {
    heapRecordPosition = position
  }

  private fun acceptInstanceDump(visitor: HProfVisitor) {
    val objectId = readId()
    val stackTraceSerialNumber = readUnsignedInt()
    val classObjectId = readId()
    val remainingBytes = readInt()
    val byteBuffer = buffer.getByteBuffer(remainingBytes)
    visitor.visitInstanceDump(objectId,
                              stackTraceSerialNumber,
                              classObjectId,
                              byteBuffer)
  }

  private fun acceptObjectArrayDump(visitor: HProfVisitor) {
    val arrayObjectId = readId()
    val stackTraceSerialNumber = readUnsignedInt()
    val numberOfElements = readUnsignedInt()
    val arrayClassObjectId = readId()
    val objects = LongArray(numberOfElements.toInt())
    for (i in objects.indices) {
      objects[i] = readId()
    }
    visitor.visitObjectArrayDump(arrayObjectId,
                                 stackTraceSerialNumber,
                                 arrayClassObjectId,
                                 objects)
  }

  private fun acceptPrimitiveArrayDump(visitor: HProfVisitor) {
    val arrayObjectId = readId()
    val stackTraceSerialNumber = readUnsignedInt()
    val numberOfElements = readUnsignedInt()
    val elementTypeId = readUnsignedByte()
    val elementType = Type.getType(elementTypeId)
    val primitiveArrayData = buffer.getByteBuffer((numberOfElements * elementType.size).toInt())
    visitor.visitPrimitiveArrayDump(
      arrayObjectId,
      stackTraceSerialNumber,
      numberOfElements,
      elementType,
      primitiveArrayData
    )
  }

  private fun acceptClassDump(visitor: HProfVisitor) {
    val classId = readId()
    val stackTraceSerialNumber = readUnsignedInt()
    val superClassId = readId()
    val classloaderClassId = readId()
    // skip signers object id, protection domain object id and 2 reserved fields
    skip((idSize * 4).toLong())
    val instanceSize = readUnsignedInt()
    val countOfConstantPool = readUnsignedShort()

    val constants = Array(countOfConstantPool) {
      val constantPoolIndex = readUnsignedShort()
      val elementType = Type.getType(readUnsignedByte())
      val value = readTypeSizeValue(elementType)
      if (elementType === Type.OBJECT) {
        ConstantPoolEntry(constantPoolIndex, elementType, remap(value))
      }
      else {
        ConstantPoolEntry(constantPoolIndex, elementType, value)
      }
    }
    val countOfStaticFields = readUnsignedShort()
    val staticFields = Array(countOfStaticFields) {
      val staticFieldStringId = readRawId()
      val elementType = Type.getType(readUnsignedByte())
      val value = readTypeSizeValue(elementType)
      if (elementType === Type.OBJECT) {
        StaticFieldEntry(staticFieldStringId, elementType, remap(value))
      }
      else {
        StaticFieldEntry(staticFieldStringId, elementType, value)
      }
    }
    val countOfInstanceFields = readUnsignedShort()
    val instanceFields = Array(countOfInstanceFields) {
      val fieldNameStringId = readRawId()
      val elementType = Type.getType(readUnsignedByte())
      InstanceFieldEntry(fieldNameStringId, elementType)
    }
    visitor.visitClassDump(
      classId,
      stackTraceSerialNumber,
      superClassId,
      classloaderClassId,
      instanceSize,
      constants,
      staticFields,
      instanceFields
    )
  }

  fun remap(id: Long): Long = remapFunction?.applyAsLong(id) ?: id

  private fun readTypeSizeValue(elementType: Type): Long {
    if (elementType === Type.OBJECT) {
      return readRawId()
    }
    when (getElementTypeSize(elementType)) {
      1 -> return buffer.get().toLong()
      2 -> return buffer.getShort().toLong()
      4 -> return buffer.getInt().toLong()
      8 -> return buffer.getLong()
    }
    throw IllegalArgumentException("Invalid size of element type.")
  }

  private fun getElementTypeSize(elementType: Type): Int {
    return if (elementType === Type.OBJECT)
      idSize
    else
      elementType.size
  }

  private fun readNonNullTerminatedString(length: Long): String {
    if (length > Integer.MAX_VALUE) {
      throw IOException("Strings larger then 2GB not supported.")
    }
    val bytes = ByteArray(length.toInt())
    buffer.get(bytes)
    return String(bytes, Charset.forName("UTF-8"))
  }

  private fun verifyFormat(version: String) {
    if (version != "JAVA PROFILE 1.0.1" && version != "JAVA PROFILE 1.0.2") {
      throw RuntimeException("Invalid format. Got: $version")
    }
  }

  private fun readNullTerminatedString(): String {
    var c: Int
    val initialPosition = buffer.position()
    do {
      c = buffer.get().toInt()
    }
    while (c > 0)
    if (c == -1) throw EOFException()
    val bytes = ByteArray((buffer.position() - initialPosition - 1).toInt())
    buffer.position(initialPosition)
    buffer.get(bytes)
    buffer.get()
    return String(bytes, Charset.forName("UTF-8"))
  }

  private fun readInt(): Int {
    return buffer.getInt()
  }

  private fun readUnsignedByte(): Int {
    return java.lang.Byte.toUnsignedInt(readByte())
  }

  private fun readByte(): Byte {
    return buffer.get()
  }

  private fun readLong(): Long {
    return buffer.getLong()
  }

  private fun readUnsignedInt(): Long {
    return buffer.getUnsignedInt()
  }

  private fun readUnsignedShort(): Int {
    return buffer.getUnsignedShort()
  }

  private fun readRawId(): Long {
    return buffer.getRawId()
  }

  private fun readId(): Long {
    return buffer.getId()
  }
}
