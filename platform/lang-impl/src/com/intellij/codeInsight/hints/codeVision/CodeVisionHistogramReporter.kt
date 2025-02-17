// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.internal.statistic.eventLog.events.EventFields
import com.intellij.internal.statistic.eventLog.events.EventPair
import com.intellij.internal.statistic.eventLog.events.FusHistogram
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class CodeVisionHistogramReporter(private val project: Project, private val scope: CoroutineScope) {
  fun submitToFus(document: Document,
                  textLength: Int,
                  fusData: CodeVisionFusData) {
    scope.launch {
      reportToFus(document, textLength, fusData)
    }
  }

  private suspend fun reportToFus(document: Document,
                                  textLength: Int,
                                  fusData: CodeVisionFusData) {
    class Result(val psiFile: PsiFile, val vcsAnnotationHistogram: FusHistogram)

    val vcsAnnotationHistResult = readAction {
      if (project.isDisposed) return@readAction null
      val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) ?: return@readAction null
      val vcsAnnotationHistogram = psiFile.getUserData(CodeVisionFusCollector.VCS_ANNOTATION_HISTOGRAM_KEY)?.build()
                                   ?: return@readAction null
      Result(psiFile, vcsAnnotationHistogram)
    }
    // we remove it because editor may be closed, but the file remains. In order not to report its data several times, we need to remove e
    if (vcsAnnotationHistResult != null) {
      val file = vcsAnnotationHistResult.psiFile
      file.putUserData(CodeVisionFusCollector.VCS_ANNOTATION_HISTOGRAM_KEY, null)
      val vcsAnnotationHistogram = vcsAnnotationHistResult.vcsAnnotationHistogram
      CodeVisionFusCollector.VCS_ANNOTATION_CALCULATION_DURATION_HISTOGRAM.log(project, vcsAnnotationHistogram.buckets.toList(),
                                                                               file.language,
                                                                               textLength)
    }

    val language = fusData.language
    for ((providerClass, histogram) in fusData.providerClassToHistogram) {
      CodeVisionFusCollector.CODE_VISION_DURATION_HISTOGRAM.log(project, listOf(
        EventPair(CodeVisionFusCollector.HISTOGRAM_FIELD, histogram.toList()),
        EventPair(EventFields.Language, language),
        EventPair(EventFields.Size, textLength),
        EventPair(CodeVisionFusCollector.PROVIDER_CLASS_FIELD, providerClass)
      ))
    }
  }
}

