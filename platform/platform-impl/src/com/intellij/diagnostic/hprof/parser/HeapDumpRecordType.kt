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

enum class HeapDumpRecordType(val value: Int) {
  NotInitialized(0x0),
  RootUnknown(0xff),
  RootGlobalJNI(0x01),
  RootLocalJNI(0x02),
  RootJavaFrame(0x03),
  RootNativeStack(0x04),
  RootStickyClass(0x05),
  RootThreadBlock(0x06),
  RootMonitorUsed(0x07),
  RootThreadObject(0x08),
  ClassDump(0x20),
  InstanceDump(0x21),
  ObjectArrayDump(0x22),
  PrimitiveArrayDump(0x23);

  companion object {
    private val map = HeapDumpRecordType.values().associateBy(HeapDumpRecordType::value)
    fun fromInt(type: Int): HeapDumpRecordType = map[type]!!
  }
}
