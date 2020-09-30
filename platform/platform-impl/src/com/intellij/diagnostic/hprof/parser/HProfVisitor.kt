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

import java.nio.ByteBuffer

open class HProfVisitor {

  private val myTopLevelVisits = BooleanArray(RecordType.HeapDumpEnd.value + 1)
  private val myHeapDumpVisits = BooleanArray(HeapDumpRecordType.RootUnknown.value + 1)

  lateinit var visitorContext: VisitorContext

  val heapRecordOffset: Long
    get() = visitorContext.currentHeapRecordOffset

  val sizeOfID: Int
    get() = visitorContext.idSize

  init {
    enableAll()
  }

  fun isEnabled(type: RecordType): Boolean {
    return myTopLevelVisits[type.value]
  }

  fun isEnabled(type: HeapDumpRecordType): Boolean {
    return myHeapDumpVisits[type.value]
  }

  fun enableAll() {
    for (recordType in RecordType.values()) {
      myTopLevelVisits[recordType.value] = true
    }
    for (recordType in HeapDumpRecordType.values()) {
      myHeapDumpVisits[recordType.value] = true
    }
  }

  fun disableAll() {
    for (recordType in RecordType.values()) {
      myTopLevelVisits[recordType.value] = false
    }
    for (recordType in HeapDumpRecordType.values()) {
      myHeapDumpVisits[recordType.value] = false
    }
  }

  fun enable(type: RecordType) {
    if (type === RecordType.HeapDump || type === RecordType.HeapDumpSegment) {
      myTopLevelVisits[RecordType.HeapDump.value] = true
      myTopLevelVisits[RecordType.HeapDumpSegment.value] = true
    }
    else {
      myTopLevelVisits[type.value] = true
    }
  }

  fun enable(type: HeapDumpRecordType) {
    enable(RecordType.HeapDump)
    myHeapDumpVisits[type.value] = true
  }

  fun disable(type: RecordType) {
    myTopLevelVisits[type.value] = false
  }

  fun disable(type: HeapDumpRecordType) {
    myHeapDumpVisits[type.value] = false
  }

  open fun preVisit() {}
  open fun postVisit() {}

  open fun visitStringInUTF8(id: Long, s: String) {}
  open fun visitLoadClass(classSerialNumber: Long, classObjectId: Long, stackSerialNumber: Long, classNameStringId: Long) {}

  open fun visitStackFrame(stackFrameId: Long,
                           methodNameStringId: Long,
                           methodSignatureStringId: Long,
                           sourceFilenameStringId: Long,
                           classSerialNumber: Long,
                           lineNumber: Int) {
  }

  open fun visitStackTrace(stackTraceSerialNumber: Long,
                           threadSerialNumber: Long,
                           numberOfFrames: Int,
                           stackFrameIds: LongArray) {
  }

  // TODO: Many of these are not implemented yet. Events are fired, but there are no parameters
  open fun visitAllocSites() {}

  open fun visitHeapSummary(totalLiveBytes: Long, totalLiveInstances: Long, totalBytesAllocated: Long, totalInstancesAllocated: Long) {}
  open fun visitStartThread() {}
  open fun visitEndThread(threadSerialNumber: Long) {}
  open fun visitHeapDump() {}
  open fun visitHeapDumpEnd() {}
  open fun visitCPUSamples() {}
  open fun visitControlSettings() {}

  open fun visitRootUnknown(objectId: Long) {}
  open fun visitRootGlobalJNI(objectId: Long, jniGlobalRefId: Long) {}
  open fun visitRootLocalJNI(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {}
  open fun visitRootJavaFrame(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {}
  open fun visitRootNativeStack(objectId: Long, threadSerialNumber: Long) {}
  open fun visitRootStickyClass(objectId: Long) {}
  open fun visitRootThreadBlock(objectId: Long, threadSerialNumber: Long) {}
  open fun visitRootMonitorUsed(objectId: Long) {}
  open fun visitRootThreadObject(objectId: Long, threadSerialNumber: Long, stackTraceSerialNumber: Long) {}

  open fun visitPrimitiveArrayDump(
    arrayObjectId: Long, stackTraceSerialNumber: Long,
    numberOfElements: Long,
    elementType: Type,
    primitiveArrayData: ByteBuffer) {
  }

  open fun visitClassDump(
    classId: Long,
    stackTraceSerialNumber: Long,
    superClassId: Long,
    classloaderClassId: Long,
    instanceSize: Long,
    constants: Array<ConstantPoolEntry>,
    staticFields: Array<StaticFieldEntry>,
    instanceFields: Array<InstanceFieldEntry>) {
  }

  open fun visitObjectArrayDump(arrayObjectId: Long, stackTraceSerialNumber: Long, arrayClassObjectId: Long, objects: LongArray) {}

  open fun visitInstanceDump(objectId: Long, stackTraceSerialNumber: Long, classObjectId: Long, bytes: ByteBuffer) {}

  open fun visitUnloadClass(classSerialNumber: Long) {}
}
