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

enum class RecordType(val value: Int) {
  NotInitialized(0x00),
  StringInUTF8(0x01),
  LoadClass(0x02),
  UnloadClass(0x03),
  StackFrame(0x04),
  StackTrace(0x05),
  AllocSites(0x06),
  HeapSummary(0x07),
  StartThread(0x0A),
  EndThread(0x0B),
  HeapDump(0x0C),
  HeapDumpSegment(0x1C),
  HeapDumpEnd(0x2C),
  CPUSamples(0x0D),
  ControlSettings(0x0E);

  companion object {
    private val map = values().associateBy(RecordType::value)
    fun fromInt(type: Int): RecordType = map[type]!!
  }

}
