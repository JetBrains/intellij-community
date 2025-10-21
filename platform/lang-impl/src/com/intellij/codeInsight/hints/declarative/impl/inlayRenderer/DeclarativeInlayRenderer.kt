// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.inlayRenderer

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.views.InlayPresentationList
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class DeclarativeInlayRenderer(
  inlayData: InlayData,
  fontMetricsStorage: InlayTextMetricsStorage,
  providerId: String,
  sourceId: String,
) : DeclarativeInlayRendererBase<InlayData>(providerId, sourceId, fontMetricsStorage) {

  override val view = InlayPresentationList(inlayData)
  @get:TestOnly
  override val presentationLists get() = listOf(presentationList)
  override fun updateModel(newModel: InlayData) {
    view.updateModel(newModel)
  }

  @get:TestOnly
  val presentationList: InlayPresentationList get() = view
}