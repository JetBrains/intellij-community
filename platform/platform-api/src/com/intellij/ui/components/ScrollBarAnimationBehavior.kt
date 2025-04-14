// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.ui.components

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.CoroutineSupport
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.UiDispatcherKind
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.serviceOrNull
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.EventQueue
import javax.swing.JScrollBar
import kotlin.coroutines.CoroutineContext

@Internal
abstract class ScrollBarAnimationBehavior(
  @JvmField protected val trackAnimator: TwoWayAnimator,
  @JvmField protected val thumbAnimator: TwoWayAnimator,
) {
  val trackFrame: Float
    get() = trackAnimator.value

  val thumbFrame: Float
    get() = thumbAnimator.value

  open fun onToggle(isOn: Boolean?) {}
  abstract fun onTrackHover(hovered: Boolean)
  abstract fun onThumbHover(hovered: Boolean)
  abstract fun onThumbMove()
  abstract fun onUninstall()
  abstract fun onReset()
}

internal open class DefaultScrollBarAnimationBehavior(
  trackAnimator: TwoWayAnimator,
  thumbAnimator: TwoWayAnimator,
) : ScrollBarAnimationBehavior(trackAnimator, thumbAnimator) {
  override fun onTrackHover(hovered: Boolean) {
    trackAnimator.start(hovered)
  }

  override fun onThumbHover(hovered: Boolean) {
    thumbAnimator.start(hovered)
  }

  override fun onThumbMove() {}

  override fun onUninstall() {
    trackAnimator.stop()
    thumbAnimator.stop()
  }

  override fun onReset() {
    trackAnimator.rewind(false)
    thumbAnimator.rewind(false)
  }
}

internal class MacScrollBarAnimationBehavior(
  coroutineScope : CoroutineScope,
  private val scrollBarComputable: () -> JScrollBar?,
  trackAnimator: TwoWayAnimator,
  thumbAnimator: TwoWayAnimator,
) : DefaultScrollBarAnimationBehavior(trackAnimator, thumbAnimator) {
  private var isTrackHovered: Boolean = false

  private val hideThumbRequests = MutableSharedFlow<Boolean>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    // Can be called early in the lifecycle when there is no application yet.
    var context = ApplicationManager.getApplication()?.serviceOrNull<CoroutineSupport>()?.uiDispatcher(UiDispatcherKind.LEGACY, false)
    if (context == null) {
      context = object : CoroutineDispatcher() {
        override fun dispatch(context: CoroutineContext, block: Runnable) {
          EventQueue.invokeLater(block)
        }

        override fun toString() = "Swing"
      }
    }
    if (ApplicationManager.getApplication() != null) {
      context += ModalityState.defaultModalityState().asContextElement()
    }
    coroutineScope.launch {
      hideThumbRequests
        .debounce(700)
        .collectLatest { start ->
          if (start) {
            withContext(context) {
              thumbAnimator.start(forward = false)
            }
          }
        }
    }
  }

  override fun onTrackHover(hovered: Boolean) {
    isTrackHovered = hovered
    val scrollBar = scrollBarComputable()
    if (scrollBar != null && DefaultScrollBarUI.isOpaque(scrollBar)) {
      trackAnimator.start(hovered)
      thumbAnimator.start(hovered)
    }
    else if (hovered) {
      trackAnimator.start(true)
    }
    else {
      thumbAnimator.start(false)
    }
  }

  override fun onThumbHover(hovered: Boolean) {}

  override fun onThumbMove() {
    val scrollBar = scrollBarComputable()
    if (scrollBar != null && scrollBar.isShowing() && !DefaultScrollBarUI.isOpaque(scrollBar)) {
      if (!isTrackHovered && thumbAnimator.value == 0f) {
        trackAnimator.rewind(false)
      }
      thumbAnimator.rewind(true)
      check(hideThumbRequests.tryEmit(!isTrackHovered))
    }
  }
}

internal open class ToggleableScrollBarAnimationBehaviorDecorator(
  private val decoratedBehavior: ScrollBarAnimationBehavior,
  trackAnimator: TwoWayAnimator,
  thumbAnimator: TwoWayAnimator,
) : ScrollBarAnimationBehavior(trackAnimator, thumbAnimator) {
  private var isOn: Boolean? = null

  override fun onToggle(isOn: Boolean?) {
    this.isOn = isOn
    isOn ?: return

    trackAnimator.start(isOn)
    thumbAnimator.start(isOn)
  }

  override fun onTrackHover(hovered: Boolean) {
    if (isOn == null) decoratedBehavior.onTrackHover(hovered)
  }
  override fun onThumbHover(hovered: Boolean) {
    if (isOn == null) decoratedBehavior.onThumbHover(hovered)
  }
  override fun onThumbMove() {
    if (isOn == null) decoratedBehavior.onThumbMove()
  }

  override fun onUninstall() {
    decoratedBehavior.onUninstall()
  }

  override fun onReset() {
    decoratedBehavior.onReset()
  }
}
