/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Map;
import java.util.TreeMap;

public class MergingUpdateQueue implements Runnable, Disposable, Activatable {

  public static final JComponent ANY_COMPONENT = new JComponent() {
  };

  private volatile boolean myActive;
  private volatile boolean mySuspended;

  private final Map<Update, Update> myScheduledUpdates = new TreeMap<Update, Update>();

  private final Alarm myWaiterForMerge;

  private volatile boolean myFlushing;

  private String myName;
  private int myMergingTimeSpan;
  private JComponent myModalityStateComponent;
  private boolean myExecuteInDispatchThread;
  private boolean myPassThrough;
  private boolean myDisposed;

  private UiNotifyConnector myUiNotifyConnector;
  private boolean myRestartOnAdd;

  public MergingUpdateQueue(@NonNls String name, int mergingTimeSpan, boolean isActive, JComponent modalityStateComponent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, null);
  }

  public MergingUpdateQueue(@NonNls String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            JComponent modalityStateComponent,
                            @Nullable Disposable parent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, null);
  }

  public MergingUpdateQueue(@NonNls String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            JComponent modalityStateComponent,
                            @Nullable Disposable parent,
                            @Nullable JComponent activationComponent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, activationComponent, true);
  }

  public MergingUpdateQueue(@NonNls String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            JComponent modalityStateComponent,
                            @Nullable Disposable parent,
                            @Nullable JComponent activationComponent,
                            boolean executeInDispatchThread) {
    myMergingTimeSpan = mergingTimeSpan;
    myModalityStateComponent = modalityStateComponent;
    myName = name;
    myPassThrough = ApplicationManager.getApplication().isUnitTestMode();
    myExecuteInDispatchThread = executeInDispatchThread;

    myWaiterForMerge = myExecuteInDispatchThread
                       ? createAlarm(Alarm.ThreadToUse.SWING_THREAD, null)
                       : createAlarm(Alarm.ThreadToUse.OWN_THREAD, this);

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


  protected Alarm createAlarm(Alarm.ThreadToUse thread, Disposable parent) {
    return new Alarm(thread, parent);
  }

  public void setMergingTimeSpan(int timeSpan) {
    myMergingTimeSpan = timeSpan;
    if (myActive) {
      restartTimer();
    }
  }

  public void cancelAllUpdates() {
    synchronized (myScheduledUpdates) {
      for (Update each : myScheduledUpdates.keySet()) {
        try {
          each.setRejected();
        }
        catch (ProcessCanceledException e) {
          continue;
        }
      }
      myScheduledUpdates.clear();
    }
  }

  public final boolean isPassThrough() {
    return myPassThrough;
  }

  public final void setPassThrough(boolean passThrough) {
    myPassThrough = passThrough;
  }



  public void activate() {
    showNotify();
  }

  public void deactivate() {
    hideNotify();
  }

  public void suspend() {
    mySuspended = true;
  }

  public void resume() {
    mySuspended = false;
    restartTimer();
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

    myActive = true;
    restartTimer();
    flush();
  }

  public void restartTimer() {
    _restart(myMergingTimeSpan);
  }

  private void _restart(final int mergingTimeSpan) {
    if (!myActive) return;

    clearWaiter();

    if (myExecuteInDispatchThread) {
      myWaiterForMerge.addRequest(this, mergingTimeSpan, getMergerModalityState());
    }
    else {
      myWaiterForMerge.addRequest(this, mergingTimeSpan);
    }
  }

  public void run() {
    if (mySuspended) return;
    flush();
  }

  public void flush() {
    synchronized (myScheduledUpdates) {
      if (myScheduledUpdates.isEmpty()) return;
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

          synchronized (myScheduledUpdates) {
            all = myScheduledUpdates.keySet().toArray(new Update[myScheduledUpdates.size()]);
            myScheduledUpdates.clear();
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

    if (myExecuteInDispatchThread && invokeLaterIfNotDispatch && !ApplicationManager.getApplication().isDispatchThread()) {
      UIUtil.invokeLaterIfNeeded(toRun);
    }
    else {
      toRun.run();
    }
  }

  public void setModalityStateComponent(JComponent modalityStateComponent) {
    myModalityStateComponent = modalityStateComponent;
  }

  protected boolean isModalityStateCorrect() {
    if (!myExecuteInDispatchThread) return true;
    if (myModalityStateComponent == ANY_COMPONENT) return true;

    ModalityState current = ApplicationManager.getApplication().getCurrentModalityState();
    final ModalityState modalityState = getModalityState();
    return !current.dominates(modalityState);
  }

  private static boolean isExpired(Update each) {
    return each.isDisposed() || each.isExpired();
  }

  protected void execute(final Update[] update) {
    for (final Update each : update) {
      if (isExpired(each)) {
        each.setRejected();
        continue;
      }

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

  private void execute(final Update each) {
    if (myDisposed) {
      each.setRejected();
    }
    else {
      each.run();
    }
  }

  public void queue(final Update update) {
    if (myPassThrough) {
      if (myDisposed) return;
      update.run();
      return;
    }

    final boolean active = myActive;
    synchronized (myScheduledUpdates) {
      if (eatThisOrOthers(update)) {
        return;
      }

      if (active && myScheduledUpdates.isEmpty()) {
        restartTimer();
      }
      put(update);

      if (myRestartOnAdd) {
        restartTimer();
      }
    }
  }

  private boolean eatThisOrOthers(Update update) {
    if (myScheduledUpdates.containsKey(update)) {
      return false;
    }

    final Update[] updates = myScheduledUpdates.keySet().toArray(new Update[myScheduledUpdates.size()]);
    for (Update eachInQueue : updates) {
      if (eachInQueue.canEat(update)) {
        return true;
      }
      if (update.canEat(eachInQueue)) {
        myScheduledUpdates.remove(eachInQueue);
        eachInQueue.setRejected();
      }
    }
    return false;
  }

  public final void run(Update update) {
    execute(new Update[]{update});
  }

  private void put(Update update) {
    final Update existing = myScheduledUpdates.remove(update);
    if (existing != null && existing != update) {
      existing.setProcessed();
      existing.setRejected();
    }
    myScheduledUpdates.put(update, update);
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
    return myName + " active=" + myActive + " scheduled=" + myScheduledUpdates;
  }

  private ModalityState getMergerModalityState() {
    return myModalityStateComponent == ANY_COMPONENT ? null : getModalityState();
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

  public MergingUpdateQueue setRestartTimerOnAdd(final boolean restart) {
    myRestartOnAdd = restart;
    return this;
  }

  public boolean isEmpty() {
    synchronized (myScheduledUpdates) {
      return myScheduledUpdates.size() == 0;
    }
  }

  public void sendFlush() {
    _restart(0);
  }
}
