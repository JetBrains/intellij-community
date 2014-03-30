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

import com.intellij.ide.UiActivity;
import com.intellij.ide.UiActivityMonitor;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
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

  private final String myName;
  private int myMergingTimeSpan;
  private JComponent myModalityStateComponent;
  private final boolean myExecuteInDispatchThread;
  private boolean myPassThrough;
  private boolean myDisposed;

  private UiNotifyConnector myUiNotifyConnector;
  private boolean myRestartOnAdd;

  private boolean myTrackUiActivity;
  private UiActivity myUiActivity;

  public MergingUpdateQueue(@NonNls @NotNull String name, int mergingTimeSpan, boolean isActive, @Nullable JComponent modalityStateComponent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, null);
  }

  public MergingUpdateQueue(@NonNls @NotNull String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            @Nullable JComponent modalityStateComponent,
                            @Nullable Disposable parent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, null);
  }

  public MergingUpdateQueue(@NonNls @NotNull String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            @Nullable JComponent modalityStateComponent,
                            @Nullable Disposable parent,
                            @Nullable JComponent activationComponent) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, activationComponent, true);
  }

  public MergingUpdateQueue(@NonNls @NotNull String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            @Nullable JComponent modalityStateComponent,
                            @Nullable Disposable parent,
                            @Nullable JComponent activationComponent,
                            boolean executeInDispatchThread) {
    this(name, mergingTimeSpan, isActive, modalityStateComponent, parent, activationComponent,
         executeInDispatchThread ? Alarm.ThreadToUse.SWING_THREAD : Alarm.ThreadToUse.POOLED_THREAD);
  }

  public MergingUpdateQueue(@NonNls @NotNull String name,
                            int mergingTimeSpan,
                            boolean isActive,
                            @Nullable JComponent modalityStateComponent,
                            @Nullable Disposable parent,
                            @Nullable JComponent activationComponent,
                            @NotNull Alarm.ThreadToUse thread) {
    myMergingTimeSpan = mergingTimeSpan;
    myModalityStateComponent = modalityStateComponent;
    myName = name;
    myPassThrough = ApplicationManager.getApplication().isUnitTestMode();
    myExecuteInDispatchThread = thread == Alarm.ThreadToUse.SWING_THREAD;
    myWaiterForMerge = createAlarm(thread, myExecuteInDispatchThread ? null : this);

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

  protected Alarm createAlarm(@NotNull Alarm.ThreadToUse thread, Disposable parent) {
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
      Update[] updates = myScheduledUpdates.keySet().toArray(new Update[myScheduledUpdates.size()]);
      for (Update each : updates) {
        try {
          each.setRejected();
        }
        catch (ProcessCanceledException ignored) {
        }
      }
      myScheduledUpdates.clear();
      finishActivity();
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

  @Override
  public void hideNotify() {
    if (!myActive) {
      return;
    }

    myActive = false;

    finishActivity();

    clearWaiter();
  }

  @Override
  public void showNotify() {
    if (myActive) {
      return;
    }

    myActive = true;
    restartTimer();
    flush();
  }

  public void restartTimer() {
    restart(myMergingTimeSpan);
  }

  private void restart(final int mergingTimeSpan) {
    if (!myActive) return;

    clearWaiter();

    if (myExecuteInDispatchThread) {
      myWaiterForMerge.addRequest(this, mergingTimeSpan, getMergerModalityState());
    }
    else {
      myWaiterForMerge.addRequest(this, mergingTimeSpan);
    }
  }

  @Override
  public void run() {
    if (mySuspended) return;
    flush();
  }

  public void flush() {
    synchronized (myScheduledUpdates) {
      if (myScheduledUpdates.isEmpty()) {
        finishActivity();
        return;
      }
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
      @Override
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
          if (isEmpty()) {
            finishActivity();
          }
        }
      }
    };

    if (myExecuteInDispatchThread && invokeLaterIfNotDispatch) {
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

  public boolean isSuspended() {
    return mySuspended;
  }

  private static boolean isExpired(@NotNull Update each) {
    return each.isDisposed() || each.isExpired();
  }

  protected void execute(@NotNull Update[] update) {
    for (final Update each : update) {
      if (isExpired(each)) {
        each.setRejected();
        continue;
      }

      if (each.executeInWriteAction()) {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
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

  private void execute(@NotNull Update each) {
    if (myDisposed) {
      each.setRejected();
    }
    else {
      each.run();
    }
  }

  public void queue(@NotNull final Update update) {
    if (myDisposed) return;

    if (myTrackUiActivity) {
      startActivity();
    }
    
    if (myPassThrough) {
      update.run();
      finishActivity();
      return;
    }

    final boolean active = myActive;
    synchronized (myScheduledUpdates) {
      try {
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
      finally {
        if (isEmpty()) {
          finishActivity();
        }
      }
    }
  }

  private boolean eatThisOrOthers(@NotNull Update update) {
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

  public final void run(@NotNull Update update) {
    execute(new Update[]{update});
  }

  private void put(@NotNull Update update) {
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

  @Override
  public void dispose() {
    myDisposed = true;
    myActive = false;
    finishActivity();
    clearWaiter();
  }

  private void clearWaiter() {
    myWaiterForMerge.cancelAllRequests();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    synchronized (myScheduledUpdates) {
      return myName + " active=" + myActive + " scheduled=" + myScheduledUpdates.size();
    }
  }

  @Nullable
  private ModalityState getMergerModalityState() {
    return myModalityStateComponent == ANY_COMPONENT ? null : getModalityState();
  }

  @NotNull
  public ModalityState getModalityState() {
    if (myModalityStateComponent == null) {
      return ModalityState.NON_MODAL;
    }
    return ModalityState.stateForComponent(myModalityStateComponent);
  }

  public void setActivationComponent(@NotNull JComponent c) {
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
      return myScheduledUpdates.isEmpty();
    }
  }

  public void sendFlush() {
    restart(0);
  }

  public boolean isFlushing() {
    return myFlushing;
  }

  public void setTrackUiActivity(boolean trackUiActivity) {
    if (myTrackUiActivity && !trackUiActivity) {
      finishActivity();
    }
    
    myTrackUiActivity = trackUiActivity;
  }

  private void startActivity() {
    if (!myTrackUiActivity) return;

    UiActivityMonitor.getInstance().addActivity(getActivityId(), getModalityState());
  }

  private void finishActivity() {
    if (!myTrackUiActivity) return;
    
    UiActivityMonitor.getInstance().removeActivity(getActivityId());    
  }

  @NotNull
  protected UiActivity getActivityId() {
    if (myUiActivity == null) {
      myUiActivity = new UiActivity.AsyncBgOperation("UpdateQueue:" + myName + hashCode());
    }
    
    return myUiActivity;
  }

}
