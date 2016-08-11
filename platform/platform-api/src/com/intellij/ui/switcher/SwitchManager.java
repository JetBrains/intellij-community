/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ui.switcher;

import com.intellij.ide.DataManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import gnu.trove.THashSet;
import org.intellij.lang.annotations.JdkConstants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Set;

public class SwitchManager {
  private final Project myProject;
  private final QuickAccessSettings myQa;

  private SwitchingSession mySession;

  private boolean myWaitingForAutoInitSession;
  private final Alarm myInitSessionAlarm = new Alarm();
  private KeyEvent myAutoInitSessionEvent;

  private final Set<SwitchingSession> myFadingAway = new THashSet<>();

  public SwitchManager(@NotNull Project project, QuickAccessSettings quickAccess) {
    myProject = project;
    myQa = quickAccess;
  }

  /**
   * internal use only
   */
  public boolean dispatchKeyEvent(@NotNull KeyEvent e) {
    if (isSessionActive()) {
      return false;
    }

    if (e.getID() != KeyEvent.KEY_PRESSED) {
      if (myWaitingForAutoInitSession) {
        cancelWaitingForAutoInit();
      }
      return false;
    }

    if (myQa.getModiferCodes().contains(e.getKeyCode())) {
      if (areAllModifiersPressed(e.getModifiers(), myQa.getModiferCodes())) {
        myWaitingForAutoInitSession = true;
        myAutoInitSessionEvent = e;
        Runnable initRunnable = () -> IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(() -> {
          if (myWaitingForAutoInitSession) {
            tryToInitSessionFromFocus(null, false);
          }
        });
        if (myFadingAway.isEmpty()) {
          myInitSessionAlarm.addRequest(initRunnable, Registry.intValue("actionSystem.keyGestureHoldTime"));
        }
        else {
          initRunnable.run();
        }
      }
    }
    else if (myWaitingForAutoInitSession) {
      cancelWaitingForAutoInit();
    }

    return false;
  }

  private ActionCallback tryToInitSessionFromFocus(@Nullable SwitchTarget preselected, boolean showSpots) {
    if (isSessionActive()) {
      return ActionCallback.REJECTED;
    }

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    SwitchProvider provider = SwitchProvider.KEY.getData(DataManager.getInstance().getDataContext(owner));
    if (provider != null) {
      return initSession(new SwitchingSession(this, provider, myAutoInitSessionEvent, preselected, showSpots));
    }

    return ActionCallback.REJECTED;
  }

  private void cancelWaitingForAutoInit() {
    myWaitingForAutoInitSession = false;
    myInitSessionAlarm.cancelAllRequests();
  }

  public static boolean areAllModifiersPressed(@JdkConstants.InputEventMask int modifiers, Set<Integer> modifierCodes) {
    int mask = 0;
    for (Integer each : modifierCodes) {
      if (each == KeyEvent.VK_SHIFT) {
        mask |= InputEvent.SHIFT_MASK;
      }

      if (each == KeyEvent.VK_CONTROL) {
        mask |= InputEvent.CTRL_MASK;
      }

      if (each == KeyEvent.VK_META) {
        mask |= InputEvent.META_MASK;
      }

      if (each == KeyEvent.VK_ALT) {
        mask |= InputEvent.ALT_MASK;
      }
    }

    return (modifiers ^ mask) == 0;
  }

  public static SwitchManager getInstance(Project project) {
    return project != null ? ServiceManager.getService(project, SwitchManager.class) : null;
  }

  public SwitchingSession getSession() {
    return mySession;
  }

  public ActionCallback initSession(SwitchingSession session) {
    cancelWaitingForAutoInit();

    disposeCurrentSession(false);
    mySession = session;
    return ActionCallback.DONE;
  }

  public void disposeCurrentSession(boolean fadeAway) {
    if (mySession != null) {
      mySession.setFadeaway(fadeAway);
      Disposer.dispose(mySession);
      mySession = null;
    }
  }

  public boolean isSessionActive() {
    return mySession != null && !mySession.isFinished();
  }

  public ActionCallback applySwitch() {
    final ActionCallback result = new ActionCallback();
    if (isSessionActive()) {
      final boolean showSpots = mySession.isShowspots();
      mySession.finish(false).doWhenDone(new Consumer<SwitchTarget>() {
        @Override
        public void consume(final SwitchTarget switchTarget) {
          mySession = null;
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
            () -> tryToInitSessionFromFocus(switchTarget, showSpots).doWhenProcessed(result.createSetDoneRunnable()));
        }
      });
    }
    else {
      result.setDone();
    }

    return result;
  }

  public boolean canApplySwitch() {
    return isSessionActive() && mySession.isSelectionWasMoved();
  }

  public boolean isSelectionWasMoved() {
    return isSessionActive() && mySession.isSelectionWasMoved();
  }

  public void addFadingAway(SwitchingSession session) {
    myFadingAway.add(session);
  }

  public void removeFadingAway(SwitchingSession session) {
    myFadingAway.remove(session);
  }
}
