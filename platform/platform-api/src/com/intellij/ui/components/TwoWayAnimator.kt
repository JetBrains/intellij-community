// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.util.Alarm
import com.intellij.util.ui.Animator

internal abstract class TwoWayAnimator(name: String,
                                       totalFrames: Int,
                                       pauseForward: Int,
                                       durationForward: Int,
                                       pauseBackward: Int,
                                       durationBackward: Int) {
  private val alarm = Alarm()
  private val forwardAnimator = lazy(LazyThreadSafetyMode.NONE) {
    MyAnimator(name = "${name}ForwardAnimator",
                                     totalFrames = totalFrames,
                                     cycleDuration = durationForward,
                                     pause = pauseForward,
                                     forward = true)
  }

  private val backwardAnimator = lazy(LazyThreadSafetyMode.NONE) {
    MyAnimator(name = """${name}BackwardAnimator""",
                                      totalFrames = totalFrames,
                                      cycleDuration = durationBackward,
                                      pause = pauseBackward,
                                      forward = false)
  }

  private val maxFrame = totalFrames - 1
  private var frame = 0

  @JvmField
  var value: Float = 0f

  abstract fun onValueUpdate()

  fun start(forward: Boolean) {
    stop()
    val animator = (if (forward) forwardAnimator else backwardAnimator).value
    if (if (forward) frame < maxFrame else frame > 0) {
      if (if (forward) frame > 0 else frame < maxFrame) {
        animator.run()
      }
      else {
        alarm.addRequest(animator, animator.pause)
      }
    }
  }

  fun rewind(forward: Boolean) {
    stop()
    if (forward) {
      if (frame != maxFrame) {
        setFrame(maxFrame)
      }
    }
    else {
      if (frame != 0) {
        setFrame(0)
      }
    }
  }

  fun stop() {
    alarm.cancelAllRequests()
    if (forwardAnimator.isInitialized()) {
      forwardAnimator.value.suspend()
    }
    if (backwardAnimator.isInitialized()) {
      backwardAnimator.value.suspend()
    }
  }

  fun setFrame(frame: Int) {
    this.frame = frame
    value = if (frame == 0) 0f else if (frame == maxFrame) 1f else frame.toFloat() / maxFrame
    onValueUpdate()
  }

  private inner class MyAnimator(name: String,
                                 totalFrames: Int,
                                 cycleDuration: Int,
                                 @JvmField val pause: Int,
                                 forward: Boolean) : Animator(name = name,
                                                              totalFrames = totalFrames,
                                                              cycleDuration = cycleDuration,
                                                              isRepeatable = false,
                                                              isForward = forward), Runnable {
    override fun run() {
      reset()
      resume()
    }

    override fun paintNow(frame: Int, totalFrames: Int, cycle: Int) {
      if (if (isForward) frame > this@TwoWayAnimator.frame else frame < this@TwoWayAnimator.frame) {
        setFrame(frame)
      }
    }

    override fun paintCycleEnd() {
      setFrame(if (isForward) maxFrame else 0)
    }
  }
}
