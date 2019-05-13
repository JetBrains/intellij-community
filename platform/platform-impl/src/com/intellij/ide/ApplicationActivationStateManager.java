// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.ApplicationImpl;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Denis Fokin
 */
public class ApplicationActivationStateManager {

  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.ApplicationActivationStateManager");

  private static final AtomicLong requestToDeactivateTime = new AtomicLong(System.currentTimeMillis());

  private static final ApplicationActivationStateManager instance = new ApplicationActivationStateManager();

  public static ApplicationActivationStateManager get () {
    return instance;
  }

  private ApplicationActivationStateManager() {}

  public enum State {
    ACTIVE,
    DEACTIVATED,
    DEACTIVATING;

    public boolean isInactive () {
      return this != ACTIVE;
    }

    public boolean isActive() {
      return this == ACTIVE;
    }
  }

  private static State state = State.DEACTIVATED;

  public static State getState() {
    return state;
  }

  public static boolean updateState (final WindowEvent windowEvent) {

    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return false;

    final Window eventWindow = windowEvent.getWindow();

    if (windowEvent.getID() == WindowEvent.WINDOW_ACTIVATED || windowEvent.getID() == WindowEvent.WINDOW_GAINED_FOCUS) {

      if (state.isInactive()) {
        Window window = windowEvent.getWindow();

        return setActive(application, window);
      }
    }
    else if (windowEvent.getID() == WindowEvent.WINDOW_DEACTIVATED && windowEvent.getOppositeWindow() == null) {
      requestToDeactivateTime.getAndSet(System.currentTimeMillis());

      // For stuff that cannot wait windowEvent notify about deactivation immediately
      if (state.isActive()) {

        IdeFrame ideFrame = getIdeFrameFromWindow(windowEvent.getWindow());
        if (ideFrame != null) {
          application.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationDeactivated(ideFrame);
        }
      }

      // We do not know for sure that application is going to be inactive,
      // windowEvent could just be showing a popup or another transient window.
      // So let's postpone the application deactivation for a while
      state = State.DEACTIVATING;
      LOG.debug("The app is in the deactivating state");

      Timer timer = UIUtil.createNamedTimer("ApplicationDeactivation",Registry.intValue("application.deactivation.timeout"), new ActionListener() {
        @Override
        public void actionPerformed(ActionEvent evt) {

          if (state == State.DEACTIVATING) {

            state = State.DEACTIVATED;
            LOG.debug("The app is in the deactivated state");

            IdeFrame ideFrame = getIdeFrameFromWindow(windowEvent.getWindow());
            if (ideFrame != null) {
              application.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).delayedApplicationDeactivated(ideFrame);
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

  private static boolean setActive(Application application, Window window) {
    IdeFrame ideFrame = getIdeFrameFromWindow(window);

    state = State.ACTIVE;
    LOG.debug("The app is in the active state");

    if (ideFrame != null) {
      application.getMessageBus().syncPublisher(ApplicationActivationListener.TOPIC).applicationActivated(ideFrame);
      return true;
    }
    return false;
  }

  public static void updateState(Window window) {
    final Application application = ApplicationManager.getApplication();
    if (!(application instanceof ApplicationImpl)) return;

    if (state.isInactive() && window != null) {
      setActive(application, window);
    }
  }

  private static IdeFrame getIdeFrameFromWindow (Window window) {
    final Component frame = UIUtil.findUltimateParent(window);
    return (frame instanceof IdeFrame) ? (IdeFrame)frame : null;
  }

}
