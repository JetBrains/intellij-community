/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.Alarm;
import com.intellij.util.AlarmFactory;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Use this class to postpone task execution and optionally merge identical tasks. This is needed e.g. to reflect in UI status of some
 * background activity: it doesn't make sense and would be inefficient to update UI 1000 times per second, so it's better to postpone 'update UI'
 * task execution for e.g. 500ms and if new updates are added during this period they can be simply ignored.
 *
 * <p/>
 * Create instance of this class and use {@link #queue(Update)} method to add new tasks.
 */
public class MergingUpdateQueue implements Runnable, Disposable, Activatable {
  public static final JComponent ANY_COMPONENT = new JComponent() {
  };

  private volatile boolean myActive;
  private volatile boolean mySuspended;

  private final TreeMap<Integer, Map<Update, Update>> myScheduledUpdates = ContainerUtil.newTreeMap();

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

  /**
   * @param name                   name of this queue, used only for debugging purposes
   * @param mergingTimeSpan        time (in milliseconds) for which execution of tasks will be postponed
   * @param isActive               if {@code true} the queue will execute tasks otherwise it'll just collect them and execute only after {@link #activate()} is called
   * @param modalityStateComponent makes sense only if {@code thread} is {@linkplain Alarm.ThreadToUse#SWING_THREAD SWING_THREAD}, in that
   *                               case the tasks will be processed in {@link ModalityState} corresponding the given component
   * @param parent                 if not {@code null} the queue will be disposed when the given parent is disposed
   * @param activationComponent    if not {@code null} the tasks will be processing only when the given component is showing
   * @param thread                 specifies on which thread the tasks are executed
   */
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
    Application app = ApplicationManager.getApplication();
    myPassThrough = app == null || app.isUnitTestMode();
    myExecuteInDispatchThread = thread == Alarm.ThreadToUse.SWING_THREAD;

    if (parent != null) {
      Disposer.register(parent, this);
    }

    myWaiterForMerge = createAlarm(thread, myExecuteInDispatchThread ? null : this);

    if (isActive) {
      showNotify();
    }

    if (activationComponent != null) {
      setActivationComponent(activationComponent);
    }
  }

  protected Alarm createAlarm(@NotNull Alarm.ThreadToUse thread, @Nullable Disposable parent) {
    return parent == null ? AlarmFactory.getInstance().create(thread) : AlarmFactory.getInstance().create(thread, parent);
  }

  public void setMergingTimeSpan(int timeSpan) {
    myMergingTimeSpan = timeSpan;
    if (myActive) {
      restartTimer();
    }
  }

  public void cancelAllUpdates() {
    synchronized (myScheduledUpdates) {
      for (Update each : getAllScheduledUpdates()) {
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

  @NotNull
  private List<Update> getAllScheduledUpdates() {
    return ContainerUtil.concat(myScheduledUpdates.values(), map -> map.keySet());
  }

  public final boolean isPassThrough() {
    return myPassThrough;
  }

  /**
   * @param passThrough if {@code true} the tasks won't be postponed but executed immediately instead (this is default mode for tests)
   */
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

  private void restart(final int mergingTimeSpanMillis) {
    if (!myActive) return;

    clearWaiter();

    if (myExecuteInDispatchThread) {
      myWaiterForMerge.addRequest(this, mergingTimeSpanMillis, getMergerModalityState());
    }
    else {
      myWaiterForMerge.addRequest(this, mergingTimeSpanMillis);
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
    if (myFlushing) {
      return;
    }
    if (!isModalityStateCorrect()) {
      return;
    }

    myFlushing = true;
    final Runnable toRun = () -> {
      try {
        final List<Update> all;

        synchronized (myScheduledUpdates) {
          all = getAllScheduledUpdates();
          myScheduledUpdates.clear();
        }

        for (Update each : all) {
          each.setProcessed();
        }

        execute(all.toArray(new Update[all.size()]));
      }
      finally {
        myFlushing = false;
        if (isEmpty()) {
          finishActivity();
        }
      }
    };

    if (myExecuteInDispatchThread) {
      UIUtil.invokeAndWaitIfNeeded(toRun);
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
        ApplicationManager.getApplication().runWriteAction(() -> execute(each));
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

  /**
   * Adds a task to be executed.
   */
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
    Map<Update, Update> updates = myScheduledUpdates.get(update.getPriority());
    if (updates != null && updates.containsKey(update)) {
      return false;
    }

    for (Update eachInQueue : getAllScheduledUpdates()) {
      if (eachInQueue.canEat(update)) {
        return true;
      }
      if (update.canEat(eachInQueue)) {
        myScheduledUpdates.get(eachInQueue.getPriority()).remove(eachInQueue);
        eachInQueue.setRejected();
      }
    }
    return false;
  }

  public final void run(@NotNull Update update) {
    execute(new Update[]{update});
  }

  private void put(@NotNull Update update) {
    Map<Update, Update> updates = myScheduledUpdates.get(update.getPriority());
    if (updates == null) {
      myScheduledUpdates.put(update.getPriority(), updates = ContainerUtil.newLinkedHashMap());
    }
    final Update existing = updates.remove(update);
    if (existing != null && existing != update) {
      existing.setProcessed();
      existing.setRejected();
    }
    updates.put(update, update);
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

  @SuppressWarnings("HardCodedStringLiteral")
  public String toString() {
    synchronized (myScheduledUpdates) {
      return myName + " active=" + myActive + " scheduled=" + getAllScheduledUpdates().size();
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
  private UiActivity getActivityId() {
    if (myUiActivity == null) {
      myUiActivity = new UiActivity.AsyncBgOperation("UpdateQueue:" + myName + hashCode());
    }
    
    return myUiActivity;
  }

}
