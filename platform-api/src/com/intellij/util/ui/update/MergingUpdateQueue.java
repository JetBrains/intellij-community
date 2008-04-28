/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.util.ui.update;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;
import java.util.TreeSet;

public class MergingUpdateQueue implements Runnable, Disposable, Activatable {

  private boolean myActive;

  private final Set<Update> mySheduledUpdates = new TreeSet<Update>();

  private Alarm myWaiterForMerge = new Alarm(Alarm.ThreadToUse.SWING_THREAD);

  private volatile boolean myFlushing;

  private String myName;
  private int myMergingTimeSpan;
  private final JComponent myModalityStateComponent;
  private boolean myPassThrough;
  private boolean myDisposed;

  private UiNotifyConnector myUiNotifyConnector;

  public MergingUpdateQueue(@NonNls String name, int mergingTimeSpan, boolean isActive, JComponent modalityStateComponent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, null);
  }

  public MergingUpdateQueue(@NonNls String name, int mergingTimeSpan, boolean isActive, JComponent modalityStateComponent, @Nullable Disposable parent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, null);
  }

  public MergingUpdateQueue(@NonNls String name, int mergingTimeSpan, boolean isActive, JComponent modalityStateComponent, @Nullable Disposable parent, @Nullable JComponent activationComponent) {
    myMergingTimeSpan = mergingTimeSpan;
    myModalityStateComponent = modalityStateComponent;
    myName = name;
    myPassThrough = ApplicationManager.getApplication().isUnitTestMode();

    if (isActive) {
      showNotify();
    }

    if (parent != null) {
      Disposer.register(parent, this);
    }

    if (activationComponent != null) {
      setActivationComponent(activationComponent);
    }
  }

  public void setMergingTimeSpan(int timeSpan) {
    myMergingTimeSpan = timeSpan;
    if (myActive) {
      restartTimer();
    }
  }

  public void cancelAllUpdates() {
    synchronized (mySheduledUpdates) {
      mySheduledUpdates.clear();
    }
  }

  public final boolean isPassThrough() {
    return myPassThrough;
  }

  public final void setPassThrough(boolean passThrough) {
    myPassThrough = passThrough;
  }

  public void hideNotify() {
    if (!myActive) {
      return;
    }

    myActive = false;
    clearWaiter();
  }

  public void showNotify() {
    if (myActive) {
      return;
    }

    restartTimer();
    myActive = true;
    flush();
  }

  private void restartTimer() {
    clearWaiter();
    myWaiterForMerge.addRequest(this, myMergingTimeSpan, getModalityState());
  }


  public void run() {
    flush();
  }

  public void flush() {
    synchronized(mySheduledUpdates) {
      if (mySheduledUpdates.isEmpty()) return;
    }
    flush(true);
  }

  public void flush(boolean invokeLaterIfNotDispatch) {
    if (myFlushing) {
      return;
    }
    if (!isModalityStateCorrect()) {
      return;
    }

    myFlushing = true;
    final Runnable toRun = new Runnable() {
      public void run() {
        try {
          final Update[] all;

          synchronized (mySheduledUpdates) {
            all = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
            mySheduledUpdates.clear();
          }

          for (Update each : all) {
            each.setProcessed();
          }

          execute(all);
        }
        finally {
          myFlushing = false;
        }
      }
    };

    if (invokeLaterIfNotDispatch && !ApplicationManager.getApplication().isDispatchThread()) {
      ApplicationManager.getApplication().invokeLater(toRun, ModalityState.NON_MODAL);
    }
    else {
      toRun.run();
    }
  }

  private boolean isModalityStateCorrect() {
    ModalityState current = ApplicationManager.getApplication().getCurrentModalityState();
    final ModalityState modalityState = getModalityState();
    return !current.dominates(modalityState);
  }

  private static boolean isExpired(Update each) {
    return each.isDisposed() || each.isExpired();
  }

  protected void execute(final Update[] update) {
    for (final Update each : update) {
      if (isExpired(each)) continue;

      if (each.executeInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          public void run() {
            execute(each);
          }
        });
      }
      else {
        execute(each);
      }
    }
  }

  private static void execute(final Update each) {
    each.run();
  }

  public void queue(final Update update) {
    final Application app = ApplicationManager.getApplication();
    if (myPassThrough) {
      app.invokeLater(new Runnable() {
        public void run() {
          if (myDisposed) return;
          update.run();
        }
      });
      return;
    }

    final boolean active = myActive;
    synchronized (mySheduledUpdates) {
      if (eatThisOrOthers(update)) {
        return;
      }

      if (active && mySheduledUpdates.isEmpty()) {
        restartTimer();
      }
      put(update);
    }
  }

  private boolean eatThisOrOthers(Update update) {
    if (mySheduledUpdates.contains(update)) {
      return false;
    }

    final Update[] updates = mySheduledUpdates.toArray(new Update[mySheduledUpdates.size()]);
    for (Update eachInQueue : updates) {
      if (eachInQueue.canEat(update)) {
        return true;
      }
      if (update.canEat(eachInQueue)) {
        mySheduledUpdates.remove(eachInQueue);
      }
    }
    return false;
  }

  public final void run(Update update) {
    execute(new Update[]{update});
  }

  private void put(Update update) {
    mySheduledUpdates.remove(update);
    mySheduledUpdates.add(update);
  }

  protected static boolean passThroughForUnitTesting() {
    return true;
  }

  public boolean isActive() {
    return myActive;
  }

  public void dispose() {
    myDisposed = true;
    myActive = false;
    clearWaiter();
  }

  private void clearWaiter() {
    myWaiterForMerge.cancelAllRequests();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return "Merger: " + myName + " active=" + myActive + " sheduled=" + mySheduledUpdates;
  }

  public ModalityState getModalityState() {
    if (myModalityStateComponent == null) {
      return ModalityState.NON_MODAL;
    }
    return ModalityState.stateForComponent(myModalityStateComponent);
  }

  public void setActivationComponent(JComponent c) {
    if (myUiNotifyConnector != null) {
      Disposer.dispose(myUiNotifyConnector);
    }

    UiNotifyConnector connector = new UiNotifyConnector(c, this);
    Disposer.register(this, connector);
    myUiNotifyConnector = connector;
  }
}
