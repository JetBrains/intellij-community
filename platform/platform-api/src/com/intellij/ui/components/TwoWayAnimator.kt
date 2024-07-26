// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.components

import com.intellij.openapi.application.EDT
import com.intellij.util.ui.Animator
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
abstract class TwoWayAnimator(
  name: String,
  totalFrames: Int,
  pauseForward: Int,
  durationForward: Int,
  pauseBackward: Int,
  durationBackward: Int,
) {
  private val animateRequests = MutableSharedFlow<MyAnimator>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val forwardAnimator = lazy(LazyThreadSafetyMode.NONE) {
    MyAnimator(
      name = "${name}ForwardAnimator",
      totalFrames = totalFrames,
      cycleDuration = durationForward,
      pauseInMs = pauseForward,
      forward = true,
    )
  }

  private val backwardAnimator = lazy(LazyThreadSafetyMode.NONE) {
    MyAnimator(
      name = """${name}BackwardAnimator""",
      totalFrames = totalFrames,
      cycleDuration = durationBackward,
      pauseInMs = pauseBackward,
      forward = false,
    )
  }

  private val maxFrame = totalFrames - 1
  private var frame = 0

  @JvmField
  var value: Float = 0f

  private var coroutineScope: CoroutineScope? = null

  abstract fun onValueUpdate()

  fun start(forward: Boolean) {
    @Suppress("SSBasedInspection")
    if (coroutineScope == null) {
      coroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("TwoWayAnimator"))
      coroutineScope!!.launch {
        animateRequests.collectLatest { animator ->
          delay(animator.pauseInMs.toLong())
          withContext(Dispatchers.EDT) {
            animator.reset()
            animator.resume()
          }
        }
      }
    }

    suspendAnimation()
    val animator = (if (forward) forwardAnimator else backwardAnimator).value
    if (if (forward) frame < maxFrame else frame > 0) {
      if (if (forward) frame > 0 else frame < maxFrame) {
        animator.reset()
        animator.resume()
      }
      else {
        check(animateRequests.tryEmit(animator))
      }
    }
  }

  fun rewind(forward: Boolean) {
    suspendAnimation()
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
    coroutineScope?.let {
      coroutineScope = null
      it.cancel()
    }
    suspendAnimation()
  }

  private fun suspendAnimation() {
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

  private inner class MyAnimator(
    name: String,
    totalFrames: Int,
    cycleDuration: Int,
    @JvmField val pauseInMs: Int,
    forward: Boolean,
  ) : Animator(
    name = name,
    totalFrames = totalFrames,
    cycleDuration = cycleDuration,
    isRepeatable = false,
    isForward = forward,
  ) {
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
