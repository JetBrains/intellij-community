// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.ApiStatus.Obsolete;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AbstractProgressIndicatorExBase extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
  private final boolean myReusable;
  private volatile ProgressIndicatorEx @Nullable [] myStateDelegates; // never updated inplace, only the whole array is replaced under getLock()
  private volatile WeakList<TaskInfo> myFinished;
  private volatile boolean myWasStarted;
  private TaskInfo myOwnerTask;

  @Obsolete
  public AbstractProgressIndicatorExBase(boolean reusable) {
    myReusable = reusable;
  }

  @Obsolete
  public AbstractProgressIndicatorExBase() {
    this(false);
  }

  @Override
  public void start() {
    synchronized (getLock()) {
      super.start();
      doDelegateRunningChange(ProgressIndicator::start);
      myWasStarted = true;
    }
  }


  @Override
  public void stop() {
    super.stop();
    doDelegateRunningChange(ProgressIndicator::stop);
  }

  @Override
  public void cancel() {
    super.cancel();
    doDelegateRunningChange(ProgressIndicator::cancel);
  }

  @Override
  public void finish(final @NotNull TaskInfo task) {
    WeakList<TaskInfo> finished = myFinished;
    if (finished == null) {
      synchronized (getLock()) {
        finished = myFinished;
        if (finished == null) {
          myFinished = finished = new WeakList<>();
        }
      }
    }
    if (!finished.addIfAbsent(task)) return;

    doDelegateRunningChange(each -> each.finish(task));
  }

  @Override
  public boolean isFinished(final @NotNull TaskInfo task) {
    Collection<TaskInfo> list = myFinished;
    return list != null && list.contains(task);
  }

  protected void setOwnerTask(TaskInfo owner) {
    myOwnerTask = owner;
  }

  @Override
  public void processFinish() {
    if (myOwnerTask != null) {
      finish(myOwnerTask);
      myOwnerTask = null;
    }
  }

  @Override
  public final void checkCanceled() {
    super.checkCanceled();

    delegate(ProgressIndicator::checkCanceled);
  }

  @Override
  public void setText(final String text) {
    super.setText(text);

    delegateProgressChange(each -> each.setText(text));
  }

  @Override
  public void setText2(final String text) {
    super.setText2(text);

    delegateProgressChange(each -> each.setText2(text));
  }

  @Override
  public void setFraction(final double fraction) {
    super.setFraction(fraction);

    delegateProgressChange(each -> each.setFraction(fraction));
  }

  @Override
  public void pushState() {
    synchronized (getLock()) {
      super.pushState();

      delegateProgressChange(ProgressIndicator::pushState);
    }
  }

  @Override
  public void popState() {
    synchronized (getLock()) {
      super.popState();

      delegateProgressChange(ProgressIndicator::popState);
    }
  }

  @Override
  protected boolean isReuseable() {
    return myReusable;
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    super.setIndeterminate(indeterminate);

    delegateProgressChange(each -> each.setIndeterminate(indeterminate));
  }

  @Override
  public final void addStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    synchronized (getLock()) {
      delegate.initStateFrom(this);
      ProgressIndicatorEx[] stateDelegates = myStateDelegates;
      if (stateDelegates == null) {
        myStateDelegates = new ProgressIndicatorEx[]{delegate};
      }
      else {
        // hard throw is essential for avoiding deadlocks
        if (ArrayUtil.contains(delegate, stateDelegates)) {
          throw new IllegalArgumentException("Already registered: " + delegate);
        }
        myStateDelegates = ArrayUtil.append(stateDelegates, delegate, ProgressIndicatorEx.class);
      }
    }
    onProgressChange();
  }

  public final void removeStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    synchronized (getLock()) {
      ProgressIndicatorEx[] delegates = myStateDelegates;
      if (delegates == null) return;
      myStateDelegates = ArrayUtil.remove(delegates, delegate);
    }
    onProgressChange();
  }

  protected final void removeAllStateDelegates() {
    synchronized (getLock()) {
      myStateDelegates = null;
    }
  }

  @ApiStatus.Internal
  protected void delegateProgressChange(@NotNull IndicatorAction action) {
    delegate(action);
    onProgressChange();
  }

  private void doDelegateRunningChange(@NotNull IndicatorAction action) {
    delegate(action);
    onRunningChange();
  }

  /**
   * @deprecated do not use. Instead, create new indicator and call {@link #addStateDelegate(ProgressIndicatorEx)} with it.
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval
  protected void delegateRunningChange(@NotNull IndicatorAction action) {
  }

  private void delegate(@NotNull IndicatorAction action) {
    ProgressIndicatorEx[] list = myStateDelegates;
    if (list != null) {
      for (ProgressIndicatorEx each : list) {
        action.execute(each);
      }
    }
  }

  @Override
  public void initStateFrom(@NotNull ProgressIndicator indicator) {
    super.initStateFrom(indicator);
    delegate(it -> it.initStateFrom(this));
  }

  protected void onProgressChange() {

  }

  protected void onRunningChange() {

  }

  @Override
  public boolean wasStarted() {
    return myWasStarted;
  }

  @FunctionalInterface
  protected interface IndicatorAction {
    void execute(@NotNull ProgressIndicatorEx each);
  }

  @Override
  public @NonNls String toString() {
    return "ProgressIndicatorEx " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled() + (isReuseable() ? "; reusable=true" : "");
  }
}
