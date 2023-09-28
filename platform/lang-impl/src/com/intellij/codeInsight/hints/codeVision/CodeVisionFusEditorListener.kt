// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.hints.codeVision.CodeVisionFusCollector.HISTOGRAM_FIELD
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.utils.StatisticsUtil
import com.intellij.lang.Language
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.concurrency.AppExecutorUtil
import kotlinx.coroutines.Runnable
import java.util.concurrent.ConcurrentHashMap

class CodeVisionFusEditorListener : EditorFactoryListener {
  override fun editorReleased(event: EditorFactoryEvent) {
    val editor = event.editor
    val project = editor.project ?: return
    val histogramBuilder = editor.getUserData(CodeVisionFusCollector.PROVIDER_STORAGE_KEY)
    if (histogramBuilder != null) {
      val fusData: CodeVisionFusData = histogramBuilder.flush()
      val document = editor.document
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document)

      val vcsAnnotationHistogram: FusHistogram? = psiFile?.getUserData(CodeVisionFusCollector.VCS_ANNOTATION_HISTOGRAM_KEY)?.build()
      // we remove it because editor may be closed, but the file remains. In order not to report its data several times, we need to remove
      psiFile?.putUserData(CodeVisionFusCollector.VCS_ANNOTATION_HISTOGRAM_KEY, null)
      val textLength = StatisticsUtil.roundToPowerOfTwo(document.textLength)

      AppExecutorUtil.getAppExecutorService().submit(Runnable {
        if (vcsAnnotationHistogram != null) {
          CodeVisionFusCollector.VCS_ANNOTATION_CALCULATION_DURATION_HISTOGRAM.log(project, vcsAnnotationHistogram.buckets.toList(), psiFile.language, textLength)
        }

        val language = fusData.language
        for ((providerClass, histogram) in fusData.providerClassToHistogram) {
          CodeVisionFusCollector.CODE_VISION_DURATION_HISTOGRAM.log(project, listOf(
            EventPair(HISTOGRAM_FIELD, histogram.toList()),
            EventPair(EventFields.Language, language),
            EventPair(EventFields.Size, textLength),
            EventPair(CodeVisionFusCollector.PROVIDER_CLASS_FIELD, providerClass)
          ))
        }
      })
    }
  }
}

internal class CodeVisionFusProviderStorage(val language: Language) {
  private val providerClassToHistogram: ConcurrentHashMap<Class<*>, FusHistogramBuilder> = ConcurrentHashMap()

  fun logProviderCollectionDuration(providerClass: Class<*>, durationMs: Long) {
    val fusHistogramBuilder = providerClassToHistogram.computeIfAbsent(providerClass,
                                                                       { FusHistogramBuilder(CodeVisionFusCollector.HISTOGRAM_BUCKETS) })
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