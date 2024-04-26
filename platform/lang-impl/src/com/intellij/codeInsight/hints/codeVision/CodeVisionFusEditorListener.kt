// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.internal.statistic.utils.StatisticsUploadAssistant
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.lang.Language
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import java.util.concurrent.ConcurrentHashMap

class CodeVisionFusEditorListener : EditorFactoryListener {
  override fun editorReleased(event: EditorFactoryEvent) {
    if (!StatisticsUploadAssistant.isCollectAllowedOrForced()) return
    val editor = event.editor
    val project = editor.project ?: return
    if (project.isDisposed) return
    val histogramBuilder = editor.getUserData(CodeVisionFusCollector.PROVIDER_STORAGE_KEY)
    val document = editor.document
    val textLength = StatisticsUtil.roundToPowerOfTwo(document.textLength)
    if (histogramBuilder != null) {
      val fusData: CodeVisionFusData = histogramBuilder.flush()
      project.service<CodeVisionHistogramReporter>().submitToFus(document, textLength, fusData)
    }
  }
}

internal class CodeVisionFusProviderStorage(val language: Language) {
  private val providerClassToHistogram: ConcurrentHashMap<Class<*>, FusHistogramBuilder> = ConcurrentHashMap()

  fun logProviderCollectionDuration(providerClass: Class<*>, durationMs: Long) {
    val fusHistogramBuilder = providerClassToHistogram.computeIfAbsent(providerClass
    ) { FusHistogramBuilder(CodeVisionFusCollector.HISTOGRAM_BUCKETS) }
    synchronized(fusHistogramBuilder) {
      fusHistogramBuilder.addValue(durationMs)
    }
  }

  internal fun flush(): CodeVisionFusData {
    val providerClassToHistogram = providerClassToHistogram.mapValues {
      synchronized(it.value) {
        it.value.build().buckets
      }
    }
    return CodeVisionFusData(language, providerClassToHistogram)
  }
}

internal class CodeVisionFusData(
  val language: Language,
  val providerClassToHistogram: Map<Class<*>, IntArray>,
)