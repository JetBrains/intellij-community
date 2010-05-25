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
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.AsyncResult;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;

public class SwitchManager implements ProjectComponent, KeyEventDispatcher, AnActionListener {

  private Project myProject;
  private QuickAccessSettings myQa;

  private SwitchingSession mySession;

  private boolean myWaitingForAutoInitSession;
  private Alarm myInitSessionAlarm = new Alarm();
  private KeyEvent myAutoInitSessionEvent;

  private Set<AnAction> mySwitchActions = new HashSet<AnAction>();

  public SwitchManager(Project project, QuickAccessSettings quickAccess, ActionManager actionManager) {
    myProject = project;
    myQa = quickAccess;


    actionManager.addAnActionListener(this, project);
    mySwitchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_UP));
    mySwitchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_DOWN));
    mySwitchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_LEFT));
    mySwitchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_RIGHT));
    mySwitchActions.add(actionManager.getAction(QuickAccessSettings.SWITCH_APPLY));
  }

  public void beforeActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
    if (!mySwitchActions.contains(action)) {
      disposeSession(mySession);
    }
  }

  public void afterActionPerformed(AnAction action, DataContext dataContext, AnActionEvent event) {
  }

  public void beforeEditorTyping(char c, DataContext dataContext) {
  }

  public boolean dispatchKeyEvent(KeyEvent e) {
      if (!myQa.isEnabled()) return false;

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

    if (myQa.getModiferCodes().contains(e.getKeyCode())) {
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
        }, Registry.intValue("actionSystem.keyGestureHoldTime"));
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
      return initSession(new SwitchingSession(this, provider, myAutoInitSessionEvent, preselected));
    }

    return new ActionCallback.Rejected();
  }

  private void cancelWaitingForAutoInit() {
    myWaitingForAutoInitSession = false;
    myAutoInitSessionEvent = null;
    myInitSessionAlarm.cancelAllRequests();
  }


  private boolean areAllModifiersPressed(int modifiers) {
    int mask = 0;
    for (Integer each : myQa.getModiferCodes()) {
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

 

  public void initComponent() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(this);
  }

  public void disposeComponent() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(this);
    myQa = null;
  }

  public static SwitchManager getInstance(Project project) {
    return project != null ? project.getComponent(SwitchManager.class) : null;
  }

  public SwitchingSession getSession() {
    return mySession;
  }

  public ActionCallback initSession(SwitchingSession session) {
    cancelWaitingForAutoInit();

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

  public boolean isSelectionWasMoved() {
    if (!isSessionActive()) return false;
    return mySession.isSelectionWasMoved();
  }
}
