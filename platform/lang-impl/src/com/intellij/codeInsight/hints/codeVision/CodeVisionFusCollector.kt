// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.internal.statistic.eventLog.EventLogGroup
import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventId3
import com.intellij.internal.statistic.eventLog.events.IntListEventField
import com.intellij.internal.statistic.eventLog.events.VarargEventId
import com.intellij.internal.statistic.service.fus.collectors.CounterUsagesCollector
import com.intellij.lang.Language
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
object CodeVisionFusCollector : CounterUsagesCollector() {
  private val GROUP = EventLogGroup("daemon.code.vision", 3)

  override fun getGroup(): EventLogGroup = GROUP

  val HISTOGRAM_FIELD: IntListEventField = EventFields.IntList("histogram")

  val PROVIDER_CLASS_FIELD = EventFields.Class("provider_class")


  val HISTOGRAM_BUCKETS = longArrayOf(0, 100, 500, 1000, 5000, 10000, 30000, 100000)

  /**
   * histogram buckets are mentioned in [HISTOGRAM_BUCKETS]
   */
  val CODE_VISION_DURATION_HISTOGRAM: VarargEventId = GROUP.registerVarargEvent("code.vision.duration",
                                                                                HISTOGRAM_FIELD,
                                                                                EventFields.Language,
                                                                                EventFields.Size,
                                                                                PROVIDER_CLASS_FIELD)

  val VCS_ANNOTATION_CALCULATION_DURATION_HISTOGRAM: EventId3<List<Int>, Language?, Int> = GROUP.registerEvent("vcs.annotation.calculation",
                                                                                                               EventFields.IntList(
                                                                                                                 "histogram"),
                                                                                                               EventFields.Language,
                                                                                                               EventFields.Size)

  internal val PROVIDER_STORAGE_KEY: Key<CodeVisionFusProviderStorage> = Key.create<CodeVisionFusProviderStorage>("code.vision.fus.provider.storage")
  internal val VCS_ANNOTATION_HISTOGRAM_KEY: Key<FusHistogramBuilder> = Key.create<FusHistogramBuilder>("vcs.annotation.histogram")

  internal fun reportCodeVisionProviderDuration(editor: Editor, language: Language, durationMs: Long, providerClass: Class<*>) {
    val fusProviderStorage = getProviderStorage(editor, language)
    fusProviderStorage.logProviderCollectionDuration(providerClass, durationMs)
  }

  private fun getProviderStorage(editor: Editor, language: Language) : CodeVisionFusProviderStorage {
    val providerStorage: CodeVisionFusProviderStorage? = editor.getUserData(PROVIDER_STORAGE_KEY)
    // not race free - but it is not a problem. We are OK if we lose data in this case
    if (providerStorage == null) {
      val newStorage = CodeVisionFusProviderStorage(language)
      editor.putUserData(PROVIDER_STORAGE_KEY, newStorage)
      return newStorage
    }
    return providerStorage
  }

  @Internal
  fun reportVcsAnnotationDuration(psiFile: PsiFile, durationMs: Long) {
    val histogramBuilder = getHistogramBuilder(psiFile)
    histogramBuilder.addValue(durationMs)
  }

  private fun getHistogramBuilder(psiFile: PsiFile) : FusHistogramBuilder {
    val vcsAnnotationHistogramBuilder: FusHistogramBuilder? = VCS_ANNOTATION_HISTOGRAM_KEY.get(psiFile)

    // not race free - but it is not a problem. We are OK if we lose data in this case
    if (vcsAnnotationHistogramBuilder == null) {
      val newHistogram = FusHistogramBuilder(HISTOGRAM_BUCKETS)
      psiFile.putUserData(VCS_ANNOTATION_HISTOGRAM_KEY, newHistogram)
      return newHistogram
    }
    return vcsAnnotationHistogramBuilder
  }
}