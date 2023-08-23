// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ApplicationActivationListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ComponentUtil
import com.intellij.util.ui.TimerUtil
import java.awt.Window
import java.awt.event.WindowEvent
import java.util.concurrent.atomic.AtomicLong

object ApplicationActivationStateManager {
  private val LOG = logger<ApplicationActivationStateManager>()

  private val requestToDeactivateTime = AtomicLong(System.currentTimeMillis())

  private var state = ApplicationActivationStateManagerState.DEACTIVATED

  val isInactive: Boolean
    get() = state.isInactive

  val isActive: Boolean
    get() = state.isActive

  fun updateState(windowEvent: WindowEvent): Boolean {
    val app = ApplicationManager.getApplication()
    if (app !is ApplicationImpl) {
      return false
    }

    if (windowEvent.id == WindowEvent.WINDOW_ACTIVATED || windowEvent.id == WindowEvent.WINDOW_GAINED_FOCUS) {
      if (state.isInactive) {
        return setActive(app, windowEvent.window)
      }
    }
    else if (windowEvent.id == WindowEvent.WINDOW_DEACTIVATED && windowEvent.getOppositeWindow() == null) {
      if (skipWindowDeactivationEvents) {
        LOG.warn("Skipped $windowEvent")
        return false
      }

      requestToDeactivateTime.getAndSet(System.currentTimeMillis())

      // for stuff that cannot wait windowEvent notify about deactivation immediately
      if (state.isActive && !app.isDisposed()) {
        val ideFrame = getIdeFrameFromWindow(windowEvent.window)
        if (ideFrame != null) {
          app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(ideFrame)
        }
      }

      // We do not know for sure that the application is going to be inactive,
      // windowEvent could just be showing a popup or another transient window.
      // So let's postpone the application deactivation for a while
      state = ApplicationActivationStateManagerState.DEACTIVATING
      LOG.debug("The app is in the deactivating state")
      val timer = TimerUtil.createNamedTimer("ApplicationDeactivation", Registry.intValue("application.deactivation.timeout", 1500)) {
        if (state != ApplicationActivationStateManagerState.DEACTIVATING) {
          return@createNamedTimer
        }

        state = ApplicationActivationStateManagerState.DEACTIVATED
        LOG.debug("The app is in the deactivated state")
        if (!app.isDisposed()) {
          val ideFrame = getIdeFrameFromWindow(windowEvent.window)
          // getIdeFrameFromWindow returns something from a UI tree, so, if not null, it must be Window
          if (ideFrame != null) {
            app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).delayedApplicationDeactivated((ideFrame as Window?)!!)
          }
        }
      }
      timer.isRepeats = false
      timer.start()
      return true
    }
    return false
  }

  private fun setActive(app: Application, window: Window?): Boolean {
    state = ApplicationActivationStateManagerState.ACTIVE
    LOG.debug("The app is in the active state")
    if (!app.isDisposed()) {
      val ideFrame = getIdeFrameFromWindow(window)
      if (ideFrame != null) {
        app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(ideFrame)
        return true
      }
    }
    return false
  }

  fun updateState(app: ApplicationImpl, window: Window) {
    if (state.isInactive) {
      setActive(app, window)
    }
  }

  private fun getIdeFrameFromWindow(window: Window?): IdeFrame? {
    return if (window == null) null else (ComponentUtil.findUltimateParent(window) as? IdeFrame)
  }
}

private enum class ApplicationActivationStateManagerState {
  ACTIVE,
  DEACTIVATED,
  DEACTIVATING;

  val isInactive: Boolean
    get() = this != ACTIVE
  val isActive: Boolean
    get() = this == ACTIVE
}