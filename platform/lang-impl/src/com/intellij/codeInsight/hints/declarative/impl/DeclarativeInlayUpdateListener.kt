// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.declarative.impl

import com.intellij.codeInsight.hints.declarative.impl.inlayRenderer.DeclarativeInlayRendererBase
import com.intellij.openapi.editor.Inlay
import com.intellij.util.messages.Topic
import org.jetbrains.annotations.ApiStatus
import java.util.EventListener

/**
 * This listener exists, because [com.intellij.openapi.editor.InlayModel.Listener.onUpdated]
 * might not be fired for some declarative inlay updates. (For example, it is not fired when only the tooltip changes).
 */
@ApiStatus.Internal
interface DeclarativeInlayUpdateListener : EventListener {
  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<DeclarativeInlayUpdateListener> = Topic(DeclarativeInlayUpdateListener::class.java, Topic.BroadcastDirection.NONE)
  }

  /** Both [oldModel] and [newModel] are the same as the result
   * of calling [inlay.renderer.toInlayData(needsUpToDateOffsets = false)][DeclarativeInlayRendererBase.toInlayData]
   * before and after the model update respectively. */
  fun afterModelUpdate(inlay: Inlay<out DeclarativeInlayRendererBase<*>>, oldModel: List<InlayData>, newModel: List<InlayData>)
}