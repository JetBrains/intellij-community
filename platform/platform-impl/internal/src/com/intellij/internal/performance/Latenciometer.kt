// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.internal.performance

import com.intellij.internal.statistic.collectors.fus.LatencyDistributionRecord
import com.intellij.internal.statistic.collectors.fus.LatencyDistributionRecordKey
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.LatencyListener
import com.intellij.openapi.editor.actionSystem.LatencyRecorder
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileDocumentManager
import org.jetbrains.annotations.ApiStatus

internal class LatencyRecorderImpl : LatencyRecorder {
  override fun recordLatencyAwareAction(editor: Editor, actionId: String, timestampMs: Long) {
    (editor as? EditorImpl)?.recordLatencyAwareAction(actionId, timestampMs)
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

