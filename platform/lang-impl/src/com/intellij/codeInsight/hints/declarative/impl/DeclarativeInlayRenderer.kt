// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class DeclarativeInlayRenderer(
  model: InlayData,
  fontMetricsStorage: InlayTextMetricsStorage,
  providerId: String,
  sourceId: String,
) : DeclarativeInlayRendererBase(providerId, sourceId, fontMetricsStorage) {

  override val view: InlayPresentationList = InlayPresentationList(model)

  @get:TestOnly
  override val presentationList: InlayPresentationList get() = view
}
