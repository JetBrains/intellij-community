// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.util.Computable
import com.intellij.util.Alarm
import javax.swing.JScrollBar

public enum class ScrollBarVisibilityPolicy {
  ON, OFF, AUTOMATIC
}

internal abstract class ScrollBarAnimationBehavior(protected val trackAnimator: TwoWayAnimator,
                                                   protected val thumbAnimator: TwoWayAnimator) {

  val trackFrame: Float
    get() = trackAnimator.myValue

  val thumbFrame: Float
    get() = thumbAnimator.myValue

  open fun onToggle(isOn: Boolean?) {}
  abstract fun onTrackHover(hovered: Boolean)
  abstract fun onThumbHover(hovered: Boolean)
  abstract fun onThumbMove()
  abstract fun onUninstall()
  abstract fun onReset()
}

internal open class DefaultScrollBarAnimationBehavior(trackAnimator: TwoWayAnimator,
                                                      thumbAnimator: TwoWayAnimator) : ScrollBarAnimationBehavior(trackAnimator,
                                                                                                                  thumbAnimator) {

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
    trackAnimator.rewind(false)
  }
}

internal class MacScrollBarAnimationBehavior(private val scrollBarComputable: Computable<JScrollBar>,
                                             trackAnimator: TwoWayAnimator,
                                             thumbAnimator: TwoWayAnimator) : DefaultScrollBarAnimationBehavior(trackAnimator,
                                                                                                                thumbAnimator) {

  private var isTrackHovered: Boolean = false
  private val hideThumbAlarm = Alarm()

  override fun onTrackHover(hovered: Boolean) {
    isTrackHovered = hovered
    val scrollBar = scrollBarComputable.compute()
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
    val scrollBar = scrollBarComputable.compute()
    if (scrollBar != null && scrollBar.isShowing() && !DefaultScrollBarUI.isOpaque(scrollBar)) {
      if (!isTrackHovered && thumbAnimator.myValue == 0f) trackAnimator.rewind(false)
      thumbAnimator.rewind(true)
      hideThumbAlarm.cancelAllRequests()
      if (!isTrackHovered) {
        hideThumbAlarm.addRequest(Runnable { thumbAnimator.start(false) }, 700)
      }
    }
  }

  override fun onUninstall() {
    hideThumbAlarm.cancelAllRequests()
    super.onUninstall()
  }
}

internal class ToggleableScrollBarAnimationBehaviorDecorator(private val decoratedBehavior: ScrollBarAnimationBehavior,
                                                             trackAnimator: TwoWayAnimator,
                                                             thumbAnimator: TwoWayAnimator) : ScrollBarAnimationBehavior(trackAnimator,
                                                                                                                        thumbAnimator) {
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
