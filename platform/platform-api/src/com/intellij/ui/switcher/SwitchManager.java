/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.openapi.actionSystem.KeyboardShortcut;
import com.intellij.openapi.actionSystem.Shortcut;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManager;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class SwitchManager implements ProjectComponent, KeyEventDispatcher, KeymapManagerListener, Keymap.Listener  {

  private Project myProject;

  private SwitchingSession mySession;

  private Set<Integer> myModifierCodes;

  private Keymap myKeymap;
  @NonNls private static final String SWITCH_UP = "SwitchUp";

  private boolean myWaitingForAutoInitSession;
  private Alarm myInitSessionAlarm = new Alarm();
  private KeyEvent myAutoInitSessionEvent;

  public SwitchManager(Project project) {
    myProject = project;
  }

  public boolean dispatchKeyEvent(KeyEvent e) {
    if (mySession != null && !mySession.isFinished()) return false;

    Component c = e.getComponent();
    Component frame = UIUtil.findUltimateParent(c);
    if (frame instanceof IdeFrame) {
      if (((IdeFrame)frame).getProject() != myProject) return false;
    }

    if (e.getID() != KeyEvent.KEY_PRESSED) {
      if (myWaitingForAutoInitSession) {
        cancelWaitingForAutoInit();
      }
      return false;
    }

    if (myModifierCodes != null && myModifierCodes.contains(e.getKeyCode())) {
      if (areAllModifiersPressed(e.getModifiers())) {
        myWaitingForAutoInitSession = true;
        myAutoInitSessionEvent = e;
        myInitSessionAlarm.addRequest(new Runnable() {
          public void run() {
            IdeFocusManager.getInstance(myProject).doWhenFocusSettlesDown(new Runnable() {
              public void run() {
                if (myWaitingForAutoInitSession) {
                  tryToInitSessionFromFocus(null);
                }
              }
            });
          }
        }, 200);
      }
    } else {
      if (myWaitingForAutoInitSession) {
        cancelWaitingForAutoInit();
      }
    }

    return false;
  }

  private ActionCallback tryToInitSessionFromFocus(@Nullable SwitchTarget preselected) {
    if (mySession != null && !mySession.isFinished()) return new ActionCallback.Rejected();

    Component owner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    SwitchProvider provider = SwitchProvider.KEY.getData(DataManager.getInstance().getDataContext(owner));
    if (provider != null) {
      return initSession(new SwitchingSession(provider, myAutoInitSessionEvent, preselected));
    }

    return new ActionCallback.Rejected();
  }

  private void cancelWaitingForAutoInit() {
    myWaitingForAutoInitSession = false;
    myAutoInitSessionEvent = null;
    myInitSessionAlarm.cancelAllRequests();
  }

  public void activeKeymapChanged(Keymap keymap) {
    KeymapManager mgr = KeymapManager.getInstance();

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(this);
    }

    myKeymap = mgr.getActiveKeymap();
    myKeymap.addShortcutChangeListener(this);

    onShortcutChanged(SWITCH_UP);
  }

  public void onShortcutChanged(String actionId) {
    if (!SWITCH_UP.equals(actionId)) return;

    Shortcut[] shortcuts = myKeymap.getShortcuts(SWITCH_UP);
    myModifierCodes = null;

    if (shortcuts.length > 0) {
      for (Shortcut each : shortcuts) {
        if (each instanceof KeyboardShortcut) {
          KeyboardShortcut kbs = (KeyboardShortcut)each;
          KeyStroke stroke = kbs.getFirstKeyStroke();
          myModifierCodes = getModifiersCodes(stroke.getModifiers());
          break;
        }
      }
    }
  }

  private boolean areAllModifiersPressed(int modifiers) {
    int mask = 0;
    for (Integer each : myModifierCodes) {
      if (each == KeyEvent.VK_SHIFT) {
        mask |= KeyEvent.SHIFT_MASK;
      }

      if (each == KeyEvent.VK_CONTROL) {
        mask |= KeyEvent.CTRL_MASK;
      }

      if (each == KeyEvent.VK_META) {
        mask |= KeyEvent.META_MASK;
      }

      if (each == KeyEvent.VK_ALT) {
        mask |= KeyEvent.ALT_MASK;
      }
    }

    return (modifiers ^ mask) == 0;
  }

  private Set<Integer> getModifiersCodes(int modifiers) {
    Set<Integer> codes = new HashSet<Integer>();
    if ((modifiers & KeyEvent.SHIFT_MASK) > 0) {
      codes.add(KeyEvent.VK_SHIFT);
    }
    if ((modifiers & KeyEvent.CTRL_MASK) > 0) {
      codes.add(KeyEvent.VK_CONTROL);
    }

    if ((modifiers & KeyEvent.META_MASK) > 0) {
      codes.add(KeyEvent.VK_META);
    }

    if ((modifiers & KeyEvent.ALT_MASK) > 0) {
      codes.add(KeyEvent.VK_ALT);
    }

    return codes;
  }

  public void initComponent() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
    KeymapManager kmMgr = KeymapManager.getInstance();
    kmMgr.addKeymapManagerListener(this);

    activeKeymapChanged(kmMgr.getActiveKeymap());
  }

  public void disposeComponent() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    KeymapManager.getInstance().removeKeymapManagerListener(this);

    if (myKeymap != null) {
      myKeymap.removeShortcutChangeListener(this);
      myKeymap = null;
    }
  }

  public static SwitchManager getInstance(Project project) {
    return project != null ? project.getComponent(SwitchManager.class) : null;
  }

  public SwitchingSession getSession() {
    return mySession;
  }

  public ActionCallback initSession(SwitchingSession session) {
    disposeSession(mySession);
    mySession = session;
    return new ActionCallback.Done();
  }

  private void disposeSession(SwitchingSession session) {
    if (mySession != null) {
      Disposer.dispose(session);
      mySession = null;
    }
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  @NotNull
  public String getComponentName() {
    return "ViewSwitchManager";
  }

  public boolean isSessionActive() {
    return mySession != null && !mySession.isFinished();
  }

  public ActionCallback applySwitch() {
    final ActionCallback result = new ActionCallback();
    if (isSessionActive()) {
      mySession.finish().doWhenDone(new AsyncResult.Handler<SwitchTarget>() {
        public void run(final SwitchTarget switchTarget) {
          mySession = null;
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(new Runnable() {
            public void run() {
              tryToInitSessionFromFocus(switchTarget).doWhenProcessed(new Runnable() {
                public void run() {
                  result.setDone();
                }
              });
            }
          });
        }
      });
    } else {
      result.setDone();
    }

    return result;
  }

  public boolean canApplySwitch() {
    return isSessionActive() && mySession.isSelectionWasMoved();
  }

  public void resetSession() {
    disposeSession(mySession);
  }
}
