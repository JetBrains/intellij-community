// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.ui.ComponentUtil;
import com.intellij.util.ui.TimerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Denis Fokin
 */
public final class ApplicationActivationStateManager {
  private static final Logger LOG = Logger.getInstance(ApplicationActivationStateManager.class);

  private static final AtomicLong requestToDeactivateTime = new AtomicLong(System.currentTimeMillis());

  private ApplicationActivationStateManager() {}

  private static ApplicationActivationStateManagerState state = ApplicationActivationStateManagerState.DEACTIVATED;

  public static boolean isInactive() {
    return state.isInactive();
  }

  public static boolean isActive() {
    return state.isActive();
  }

  public static boolean updateState(@NotNull WindowEvent windowEvent) {
    Application app = ApplicationManager.getApplication();
    if (!(app instanceof ApplicationImpl)) {
      return false;
    }

    if (windowEvent.getID() == WindowEvent.WINDOW_ACTIVATED || windowEvent.getID() == WindowEvent.WINDOW_GAINED_FOCUS) {
      if (state.isInactive()) {
        return setActive(app, windowEvent.getWindow());
      }
    }
    else if (windowEvent.getID() == WindowEvent.WINDOW_DEACTIVATED && windowEvent.getOppositeWindow() == null) {
      requestToDeactivateTime.getAndSet(System.currentTimeMillis());

      // for stuff that cannot wait windowEvent notify about deactivation immediately
      if (state.isActive() && !app.isDisposed()) {
        IdeFrame ideFrame = getIdeFrameFromWindow(windowEvent.getWindow());
        if (ideFrame != null) {
          app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(ideFrame);
        }
      }

      // We do not know for sure that application is going to be inactive,
      // windowEvent could just be showing a popup or another transient window.
      // So let's postpone the application deactivation for a while
      state = ApplicationActivationStateManagerState.DEACTIVATING;
      LOG.debug("The app is in the deactivating state");

      Timer timer = TimerUtil.createNamedTimer("ApplicationDeactivation", Registry.intValue("application.deactivation.timeout", 1500), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent evt) {
          if (state == ApplicationActivationStateManagerState.DEACTIVATING) {
            //noinspection AssignmentToStaticFieldFromInstanceMethod
            state = ApplicationActivationStateManagerState.DEACTIVATED;
            LOG.debug("The app is in the deactivated state");

            if (!app.isDisposed()) {
              IdeFrame ideFrame = getIdeFrameFromWindow(windowEvent.getWindow());
              // getIdeFrameFromWindow returns something from UI tree, so, if not null, it must be Window
              if (ideFrame != null) {
                app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).delayedApplicationDeactivated(((Window)ideFrame));
              }
            }
          }
        }
      });

      timer.setRepeats(false);
      timer.start();
      return true;
    }
    return false;
  }

  private static boolean setActive(@NotNull Application app, @Nullable Window window) {
    state = ApplicationActivationStateManagerState.ACTIVE;
    LOG.debug("The app is in the active state");

    if (!app.isDisposed()) {
      IdeFrame ideFrame = getIdeFrameFromWindow(window);
      if (ideFrame != null) {
        app.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(ideFrame);
        return true;
      }
    }
    return false;
  }

  public static void updateState(@NotNull ApplicationImpl app, @NotNull Window window) {
    if (state.isInactive()) {
      setActive(app, window);
    }
  }

  @Nullable
  private static IdeFrame getIdeFrameFromWindow(@Nullable Window window) {
    Component frame = window == null ? null : ComponentUtil.findUltimateParent(window);
    return frame instanceof IdeFrame ? (IdeFrame)frame : null;
  }
}

enum ApplicationActivationStateManagerState {
  ACTIVE,
  DEACTIVATED,
  DEACTIVATING;

  public boolean isInactive() {
    return this != ACTIVE;
  }

  public boolean isActive() {
    return this == ACTIVE;
  }
}