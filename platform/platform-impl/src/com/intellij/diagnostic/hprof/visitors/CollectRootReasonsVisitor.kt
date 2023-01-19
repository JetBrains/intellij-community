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

import com.intellij.diagnostic.hprof.classstore.ThreadInfo
import com.intellij.diagnostic.hprof.navigator.RootReason
import com.intellij.diagnostic.hprof.parser.HProfVisitor
import com.intellij.diagnostic.hprof.parser.HeapDumpRecordType
import it.unimi.dsi.fastutil.longs.Long2ObjectMap
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap

class CollectRootReasonsVisitor(private val threadsMap: Long2ObjectMap<ThreadInfo>) : HProfVisitor() {
  val roots = Long2ObjectOpenHashMap<RootReason>()

  override fun preVisit() {
    disableAll()
    enable(HeapDumpRecordType.RootGlobalJNI)
    enable(HeapDumpRecordType.RootJavaFrame)
    enable(HeapDumpRecordType.RootLocalJNI)
    enable(HeapDumpRecordType.RootMonitorUsed)
    enable(HeapDumpRecordType.RootNativeStack)
    enable(HeapDumpRecordType.RootStickyClass)
    enable(HeapDumpRecordType.RootThreadBlock)
    enable(HeapDumpRecordType.RootThreadObject)
    enable(HeapDumpRecordType.RootUnknown)
  }

  override fun visitRootUnknown(objectId: Long) {
    roots.put(objectId, RootReason.rootUnknown)
  }

  override fun visitRootGlobalJNI(objectId: Long, jniGlobalRefId: Long) {
    roots.put(objectId, RootReason.rootGlobalJNI)
  }

  override fun visitRootLocalJNI(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {
    roots.put(objectId, RootReason.rootLocalJNI)
  }

  override fun visitRootJavaFrame(objectId: Long, threadSerialNumber: Long, frameNumber: Long) {
    val rootReason =
      if (frameNumber >= 0) {
        RootReason.createJavaFrameReason(threadsMap[threadSerialNumber].frames[frameNumber.toInt()])
      }
      else {
        RootReason.createJavaFrameReason("Unknown location")
      }
    // Java frame has a lower priority - if won't override any other GC-root reasons.
    if (!roots.containsKey(objectId)) {
      roots.put(objectId, rootReason)
    }
  }

  override fun visitRootNativeStack(objectId: Long, threadSerialNumber: Long) {
    roots.put(objectId, RootReason.rootNativeStack)
  }

  override fun visitRootStickyClass(objectId: Long) {
    roots.put(objectId, RootReason.rootStickyClass)
  }

  override fun visitRootThreadBlock(objectId: Long, threadSerialNumber: Long) {
    roots.put(objectId, RootReason.rootThreadBlock)
  }

  override fun visitRootThreadObject(objectId: Long, threadSerialNumber: Long, stackTraceSerialNumber: Long) {
    roots.put(objectId, RootReason.rootThreadObject)
  }

  override fun visitRootMonitorUsed(objectId: Long) {
    roots.put(objectId, RootReason.rootMonitorUsed)
  }
}
