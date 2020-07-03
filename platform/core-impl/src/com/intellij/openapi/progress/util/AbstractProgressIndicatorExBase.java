/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.progress.util;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.TaskInfo;
import com.intellij.openapi.wm.ex.ProgressIndicatorEx;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.WeakList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class AbstractProgressIndicatorExBase extends AbstractProgressIndicatorBase implements ProgressIndicatorEx {
  private final boolean myReusable;
  private volatile ProgressIndicatorEx @Nullable [] myStateDelegates; // never updated inplace, only the whole array is replaced under getLock()
  private volatile WeakList<TaskInfo> myFinished;
  private volatile boolean myWasStarted;
  private TaskInfo myOwnerTask;

  public AbstractProgressIndicatorExBase(boolean reusable) {
    myReusable = reusable;
  }

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
  public void finish(@NotNull final TaskInfo task) {
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
  public boolean isFinished(@NotNull final TaskInfo task) {
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
  }

  public final void removeStateDelegate(@NotNull ProgressIndicatorEx delegate) {
    synchronized (getLock()) {
      ProgressIndicatorEx[] delegates = myStateDelegates;
      if (delegates == null) return;
      myStateDelegates = ArrayUtil.remove(delegates, delegate);
    }
  }

  protected final void removeAllStateDelegates() {
    synchronized (getLock()) {
      myStateDelegates = null;
    }
  }

  private void delegateProgressChange(@NotNull IndicatorAction action) {
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
}
