// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide

import com.intellij.openapi.application.*
import com.intellij.openapi.application.impl.ApplicationImpl
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.IdeFrame
import com.intellij.ui.ComponentUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Window
import java.awt.event.WindowEvent
import kotlin.time.Duration.Companion.milliseconds

@Internal
object ApplicationActivationStateManager {
  private val LOG = logger<ApplicationActivationStateManager>()

  private var state = ApplicationActivationStateManagerState.DEACTIVATED
  private var delayedDeactivatedJob: Job? = null

  val isActive: Boolean
    get() = state.isActive

  fun updateState(windowEvent: WindowEvent): Boolean {
    val app = ApplicationManager.getApplication()
    if (app !is ApplicationImpl || app.isExitInProgress || app.isDisposed) {
      return false
    }

    val window = windowEvent.window
    if (windowEvent.id == WindowEvent.WINDOW_ACTIVATED || windowEvent.id == WindowEvent.WINDOW_GAINED_FOCUS) {
      if (state.isInactive) {
        return setActive(app, window)
      }
    }
    else if (windowEvent.id == WindowEvent.WINDOW_DEACTIVATED && windowEvent.getOppositeWindow() == null) {
      if (skipWindowDeactivationEvents) {
        LOG.warn("Skipped $windowEvent")
        return false
      }

      // for stuff that cannot wait windowEvent notify about deactivation immediately
      if (state.isActive) {
        val ideFrame = getIdeFrameFromWindow(window)
        if (ideFrame != null) {
          app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(ideFrame)
        }
      }

      // We do not know for sure that the application is going to be inactive,
      // windowEvent could just be showing a popup or another transient window.
      // So let's postpone the application deactivation for a while
      state = ApplicationActivationStateManagerState.DEACTIVATING
      LOG.debug("The app is in the deactivating state")
      delayedDeactivatedJob?.cancel()
      delayedDeactivatedJob = app.getCoroutineScope().launch(CoroutineName("ApplicationDeactivation")) {
        delay(Registry.intValue("application.deactivation.timeout", 1_500).milliseconds)
        withContext(Dispatchers.ui(CoroutineSupport.UiDispatcherKind.STRICT) + ModalityState.any().asContextElement()) {
          if (state != ApplicationActivationStateManagerState.DEACTIVATING) {
            return@withContext
          }

          state = ApplicationActivationStateManagerState.DEACTIVATED
          LOG.debug("The app is in the deactivated state")
          val ideFrame = getIdeFrameFromWindow(window)
          if (ideFrame != null) {
            // getIdeFrameFromWindow returns something from a UI tree, so, if not null, it must be Window
            val publisher = app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC)
            withContext(Dispatchers.UiWithModelAccess) {
              publisher.delayedApplicationDeactivated(ideFrame as Window)
            }
          }
        }
      }
      return true
    }
    return false
  }

  private fun setActive(app: Application, window: Window?): Boolean {
    state = ApplicationActivationStateManagerState.ACTIVE
    LOG.debug("The app is in the active state")
    delayedDeactivatedJob?.cancel()
    delayedDeactivatedJob = null
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
