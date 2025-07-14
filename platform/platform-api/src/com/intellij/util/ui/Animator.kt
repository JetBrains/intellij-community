// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.*
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.util.ReflectionUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Obsolete
import org.jetbrains.annotations.NonNls
import java.awt.GraphicsEnvironment
import javax.swing.SwingUtilities
import kotlin.time.Duration.Companion.microseconds

abstract class Animator @JvmOverloads constructor(
  private val name: @NonNls String?,
  private val totalFrames: Int,
  private val cycleDuration: Int,
  private val isRepeatable: Boolean,
  @JvmField protected val isForward: Boolean = true,
  coroutineScope: CoroutineScope? = null,
) {
  private var ticker: Job? = null
  private var currentFrame = 0
  private var startTime: Long = 0
  private var startDeltaTime: Long = 0
  private var initialStep = false
  private val coroutineScope = coroutineScope ?: if (isRepeatable) animatorCoroutineScopeWithError(name) else animatorCoroutineScope(name)

  @JvmOverloads
  constructor(
    name: @NonNls String?,
    totalFrames: Int,
    cycleDuration: Int,
    isRepeatable: Boolean,
    isForward: Boolean = true,
    disposable: Disposable,
  ) : this(
    name = name,
    totalFrames = totalFrames,
    cycleDuration = cycleDuration,
    isRepeatable = isRepeatable,
    isForward = isForward,
    coroutineScope = animatorCoroutineScope(name, disposable),
  )

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

  /**
   * This operation is used for manual processing of animation in cases when the IDE event queue is unavailable
   */
  @ApiStatus.Internal
  fun forceTick() {
    onTick()
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
    ticker.cancel()
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
      val context = if (ApplicationManager.getApplication() == null) {
        RawSwingDispatcher
      }
      else {
        Dispatchers.ui(uiKind()) + ModalityState.any().asContextElement()
      }
      ticker = coroutineScope.launch(context) {
        while (true) {
          onTick()
          delay((cycleDuration * 1000L / totalFrames).microseconds)
        }
      }
    }
  }

  @ApiStatus.Internal
  protected open fun uiKind(): UiDispatcherKind = UiDispatcherKind.RELAX

  abstract fun paintNow(frame: Int, totalFrames: Int, cycle: Int)

  /**
   * Prefer passing a [CoroutineScope] or [Disposable] constructor argument instead.
   *
   * Prefer not to override the method: it is not called by the animator itself, nor is a part of [Disposable] contract.
   */
  open fun dispose() {
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
    return "Animator '$name' @" + System.identityHashCode(this) +
           if (future == null || future.isCompleted) " (stopped)" else " (running $currentFrame/$totalFrames frame)"
  }
}

private fun skipAnimation(): Boolean {
  if (GraphicsEnvironment.isHeadless()) {
    return true
  }

  val app = ApplicationManager.getApplication()
  return app != null && app.isUnitTestMode()
}

/**
 * A 'runaway' animator will not only leak itself and its repainter, but also everything else that happened to be close in the Swing hierarchy.
 * That means whole toolwindow tabs and FileEditors worth of memory.
 */
@Suppress("RAW_SCOPE_CREATION")
private fun animatorCoroutineScopeWithError(name: String?): CoroutineScope {
  val realName = name ?: getCallerClassName()
  logger<Animator>().error("Do not use repeatable animators without an explicit lifetime scope. " +
                           "An explicit Disposable would at least let us log memory leaks and runaway tasks.")
  return CoroutineScope(SupervisorJob() +
                        Dispatchers.Default +
                        ModalityState.defaultModalityState().asContextElement() +
                        ClientId.coroutineContext() +
                        CoroutineName("Dangerously utilized ui.Animator by $realName"))
}

internal fun animatorCoroutineScope(name: String?, disposable: Disposable): CoroutineScope {
  if (disposable is Application) {
    throw IllegalArgumentException("Please use a real disposable")
  }
  val scope = animatorCoroutineScope(name)
  Disposer.register(disposable) { scope.cancel() }
  return scope
}

@Suppress("RAW_SCOPE_CREATION")
internal fun animatorCoroutineScope(name: String?): CoroutineScope {
  val realName = name ?: getCallerClassName()
  val scope = CoroutineScope(SupervisorJob() +
                             Dispatchers.Default +
                             ModalityState.defaultModalityState().asContextElement() +
                             ClientId.coroutineContext() +
                             CoroutineName("ui.Animator by $realName"))
  return scope
}

private fun getCallerClassName(): String? {
  // fight '@JvmOverloads' and constructor delegations
  for (i in 5..10) {
    val callerClazz = ReflectionUtil.getCallerClass(i)
    if (callerClazz == Animator::class.java) continue
    return callerClazz.name
  }
  return "Unknown Caller"
}
