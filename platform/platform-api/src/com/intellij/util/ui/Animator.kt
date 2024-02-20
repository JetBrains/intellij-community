// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.util.concurrency.EdtScheduledExecutorService
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import java.awt.GraphicsEnvironment
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.microseconds

abstract class Animator @JvmOverloads constructor(private val name: @NonNls String?,
                                                  private val totalFrames: Int,
                                                  private val cycleDuration: Int,
                                                  private val isRepeatable: Boolean,
                                                  @JvmField protected val isForward: Boolean = true,
                                                  coroutineScope: CoroutineScope? = null) : Disposable {
  private var ticker: Any? = null
  private var currentFrame = 0
  private var startTime: Long = 0
  private var startDeltaTime: Long = 0
  private var initialStep = false
  private val coroutineScope = coroutineScope ?: CoroutineScope(SupervisorJob() + Dispatchers.Default)

  @Obsolete
  fun isForward(): Boolean = isForward

  @Volatile
  var isDisposed: Boolean = false
    private set

  init {
    reset()
    if (skipAnimation()) {
      animationDone()
    }
  }

  private fun onTick() {
    if (isDisposed) {
      return
    }

    if (initialStep) {
      initialStep = false
      // keep animation state on suspend
      startTime = System.currentTimeMillis() - startDeltaTime
      paint()
      return
    }

    val cycleTime = (System.currentTimeMillis() - startTime).toDouble()
    if (cycleTime < 0) {
      // currentTimeMillis() is not monotonic - let's pretend that animation didn't change
      return
    }

    var newFrame = (cycleTime * totalFrames / cycleDuration).toLong()
    if (isRepeatable) {
      newFrame %= totalFrames.toLong()
    }
    if (newFrame == currentFrame.toLong()) {
      return
    }

    if (!isRepeatable && newFrame >= totalFrames) {
      animationDone()
      return
    }

    currentFrame = newFrame.toInt()
    paint()
  }

  private fun paint() {
    paintNow(frame = if (isForward) currentFrame else totalFrames - currentFrame - 1, totalFrames = totalFrames, cycle = cycleDuration)
  }

  private fun animationDone() {
    stopTicker()
    if (!isDisposed) {
      SwingUtilities.invokeLater(::paintCycleEnd)
    }
  }

  private fun stopTicker() {
    val ticker = ticker ?: return
    this.ticker = null
    if (ticker is Job) {
      ticker.cancel()
    }
    else if (ticker is ScheduledFuture<*>) {
      ticker.cancel(false)
    }
  }

  protected open fun paintCycleEnd() {}
  fun suspend() {
    startDeltaTime = System.currentTimeMillis() - startTime
    initialStep = true
    stopTicker()
  }

  open fun resume() {
    if (isDisposed) {
      stopTicker()
      return
    }

    if (skipAnimation()) {
      animationDone()
      return
    }

    if (cycleDuration == 0) {
      currentFrame = totalFrames - 1
      paint()
      animationDone()
    }
    else if (ticker == null) {
      if (ApplicationManager.getApplication() == null) {
        ticker = EdtScheduledExecutorService.getInstance().scheduleWithFixedDelay(object : java.lang.Runnable {
          override fun run() {
            onTick()
          }

          override fun toString(): String {
            return "Scheduled " + this@Animator
          }
        }, 0, cycleDuration * 1000L / totalFrames, TimeUnit.MICROSECONDS)

        return
      }

      ticker = coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        while (true) {
          onTick()
          delay((cycleDuration * 1000L / totalFrames).microseconds)
        }
      }
    }
  }

  abstract fun paintNow(frame: Int, totalFrames: Int, cycle: Int)

  override fun dispose() {
    stopTicker()
    coroutineScope.cancel()
    isDisposed = true
  }

  val isRunning: Boolean
    get() = ticker != null

  fun reset() {
    currentFrame = 0
    startDeltaTime = 0
    initialStep = true
  }

  override fun toString(): String {
    val future = ticker
    var stopped = future == null
    if (future is Job) {
      stopped = future.isCompleted
    }
    else if (future is ScheduledFuture<*>) {
      stopped = future.isDone
    }
    return "Animator '$name' @" + System.identityHashCode(this) +
           if (stopped) " (stopped)" else " (running $currentFrame/$totalFrames frame)"
  }
}

private fun skipAnimation(): Boolean {
  if (GraphicsEnvironment.isHeadless()) {
    return true
  }

  val app = ApplicationManager.getApplication()
  return app != null && app.isUnitTestMode()
}

