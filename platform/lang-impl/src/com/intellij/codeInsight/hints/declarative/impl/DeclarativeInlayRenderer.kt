// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.declarative.impl.views.SingleDeclarativeHintView
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

  override val view = SingleDeclarativeHintView(inlayData)
  @get:TestOnly
  override val presentationLists get() = listOf(presentationList)

  @get:TestOnly
  val presentationList: InlayPresentationList get() = view.presentationList
}
