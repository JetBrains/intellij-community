// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl.inlayRenderer

import com.intellij.codeInsight.hints.declarative.impl.InlayData
import com.intellij.codeInsight.hints.declarative.impl.InlayPresentationList
import com.intellij.codeInsight.hints.declarative.impl.views.IndentedDeclarativeHintView
import com.intellij.codeInsight.hints.declarative.impl.views.MultipleDeclarativeHintsView
import com.intellij.codeInsight.hints.declarative.impl.views.SingleDeclarativeHintView
import com.intellij.codeInsight.hints.presentation.InlayTextMetricsStorage
import com.intellij.openapi.editor.Inlay
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly

@ApiStatus.Internal
class DeclarativeIndentedBlockInlayRenderer(
  inlayData: List<InlayData>,
  fontMetricsStorage: InlayTextMetricsStorage,
  providerId: String,
  sourceId: String,
  indentAnchorOffsetHint: Int,
) : DeclarativeInlayRendererBase<List<InlayData>>(providerId, sourceId, fontMetricsStorage) {

  override val view = IndentedDeclarativeHintView(MultipleDeclarativeHintsView(inlayData), indentAnchorOffsetHint)

  override val presentationLists get() = view.view.presentationLists

  override fun initInlay(inlay: Inlay<out DeclarativeInlayRendererBase<List<InlayData>>>) {
    super.initInlay(inlay)
    view.inlay = inlay
  }
}