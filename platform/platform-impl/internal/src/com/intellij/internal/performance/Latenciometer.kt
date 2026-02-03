// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.internal.performance

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import it.unimi.dsi.fastutil.ints.IntArrayList
import org.jetbrains.annotations.ApiStatus

internal class LatencyRecorderImpl : LatencyRecorder {
  override fun recordLatencyAwareAction(editor: Editor, actionId: String, timestampMs: Long) {
    (editor as? EditorImpl)?.recordLatencyAwareAction(actionId, timestampMs)
  }
}

class LatencyRecord {
  var totalLatency: Long = 0L
  var maxLatency: Int = 0
  val samples: IntArrayList = IntArrayList()
  private var samplesSorted = false

  fun update(latencyInMS: Int) {
    samplesSorted = false
    samples.add(latencyInMS)
    totalLatency += latencyInMS
    if (latencyInMS > maxLatency) {
      maxLatency = latencyInMS
    }
  }

  val averageLatency: Long
    get() = totalLatency / samples.size

  fun percentile(n: Int): Int {
    if (!samplesSorted) {
      samples.sort()
      samplesSorted = true
    }
    val index = (samples.size * n / 100).coerceAtMost(samples.size - 1)
    return samples.getInt(index)
  }
}

@ApiStatus.Internal
data class LatencyDistributionRecordKey(val name: String) {
  var details: String? = null
}

@ApiStatus.Internal
class LatencyDistributionRecord(val key: LatencyDistributionRecordKey) {
  val totalLatency: LatencyRecord = LatencyRecord()
  val actionLatencyRecords: MutableMap<String, LatencyRecord> = mutableMapOf()

  fun update(action: String, latencyInMS: Int) {
    totalLatency.update(latencyInMS)
    actionLatencyRecords.getOrPut(action) { LatencyRecord() }.update(latencyInMS)
  }
}

val latencyMap: MutableMap<LatencyDistributionRecordKey, LatencyDistributionRecord> = mutableMapOf()

var currentLatencyRecordKey: LatencyDistributionRecordKey? = null

val latencyRecorderProperties: MutableMap<String, String> = mutableMapOf()

private class LatenciometerListener : LatencyListener {
  override fun recordTypingLatency(editor: Editor, action: String, latencyInMS: Long) {
    val key = currentLatencyRecordKey ?: run {
      val fileType = FileDocumentManager.getInstance().getFile(editor.document)?.fileType ?: return
      LatencyDistributionRecordKey(fileType.name)
    }
    val latencyRecord = latencyMap.getOrPut(key) {
      LatencyDistributionRecord(key)
    }
    latencyRecord.update(getActionKey(action), latencyInMS.toInt())
  }
}

private fun getActionKey(action: String): String {
  return if (action.length == 1) {
    when(action[0]) {
      in 'A'..'Z', in 'a'..'z', in '0'..'9' -> "Letter"
      ' ' -> "Space"
      '\n' -> "Enter"
      else -> action
    }
  }
  else action
}

