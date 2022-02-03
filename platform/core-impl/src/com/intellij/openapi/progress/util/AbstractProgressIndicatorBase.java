// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

import com.intellij.diagnostic.PluginException;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.application.TransactionGuardImpl;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.impl.CoreProgressManager;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.ui.CoreAwareIconManager;
import com.intellij.ui.IconManager;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AbstractProgressIndicatorBase extends UserDataHolderBase implements ProgressIndicator {
  private static final Logger LOG = Logger.getInstance(AbstractProgressIndicatorBase.class);

  private volatile @NlsContexts.ProgressText String myText;
  private volatile double myFraction;
  private volatile @NlsContexts.ProgressDetails String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;
  private volatile boolean myStopped;

  private volatile boolean myIndeterminate = Boolean.parseBoolean(System.getProperty("ide.progress.indeterminate.by.default", "true"));
  private volatile Runnable myMacActivity;
  // false by default - do not attempt to use such a relatively heavy code on start-up
  private volatile boolean myShouldStartActivity = SystemInfoRt.isMac && Boolean.parseBoolean(System.getProperty("idea.mac.prevent.app.nap", "true"));

  private static class State {
    private final @NlsContexts.ProgressText String myText;
    private final @NlsContexts.ProgressDetails String myText2;
    private final double myFraction;
    private final boolean myIndeterminate;

    private State(@NlsContexts.ProgressText String text, @NlsContexts.ProgressDetails String text2, double fraction, boolean indeterminate) {
      myText = text;
      myText2 = text2;
      myFraction = fraction;
      myIndeterminate = indeterminate;
    }
  }

  private Stack<State> myStateStack; // guarded by getLock()

  private volatile ProgressIndicator myModalityProgress;
  private volatile ModalityState myModalityState = ModalityState.NON_MODAL;
  private volatile int myNonCancelableSectionCount;
  @SuppressWarnings("SpellCheckingInspection") private final Object lock = ObjectUtils.sentinel("APIB lock");

  @Override
  public void start() {
    synchronized (getLock()) {
      if (isRunning()) {
        throwInvalidState("Attempt to start ProgressIndicator which is already running");
      }
      if (myStopped) {
        if (myCanceled && !isReuseable()) {
          throwInvalidState("Attempt to start ProgressIndicator which is cancelled and already stopped");
        }
        myCanceled = false;
        myStopped = false;
      }

      myText = "";
      myFraction = 0;
      myText2 = "";

      if (myShouldStartActivity) {
        IconManager iconManager = IconManager.getInstance();
        if (iconManager instanceof CoreAwareIconManager) {
          myMacActivity = ((CoreAwareIconManager)iconManager).wakeUpNeo(this);
        }
      }
      else {
        myMacActivity = null;
      }
      myRunning = true;
    }
  }

  protected boolean isReuseable() {
    return false;
  }

  @Override
  public void stop() {
    synchronized (getLock()) {
      if (myStopped) {
        throwInvalidState("Attempt to stop ProgressIndicator which is already stopped");
      }
      if (!myRunning) {
        throwInvalidState("stop() should be called only if start() called before");
      }
      myRunning = false;
      myStopped = true;
      stopSystemActivity();
    }
  }

  private void throwInvalidState(@NotNull String message) {
    LOG.error(message + ": " + this + "," + getClass(), new IllegalStateException());
  }

  void stopSystemActivity() {
    Runnable macActivity = myMacActivity;
    if (macActivity != null) {
      macActivity.run();
      myMacActivity = null;
    }
  }

  @Override
  public boolean isRunning() {
    return myRunning;
  }

  @Override
  public void cancel() {
    myCanceled = true;
    stopSystemActivity();
    if (ApplicationManager.getApplication() != null) {
      ProgressManager.canceled(this);
    }
  }

  @Override
  public boolean isCanceled() {
    return myCanceled;
  }

  @Override
  public void checkCanceled() {
    throwIfCanceled();
    if (CoreProgressManager.runCheckCanceledHooks(this)) {
      throwIfCanceled();
    }
  }

  private void throwIfCanceled() {
    if (isCanceled() && isCancelable()) {
      Throwable trace = getCancellationTrace();
      throw trace instanceof ProcessCanceledException ? (ProcessCanceledException)trace : new ProcessCanceledException(trace);
    }
  }

  protected @Nullable Throwable getCancellationTrace() {
    return this instanceof Disposable ? Disposer.getDisposalTrace((Disposable)this) : null;
  }

  @Override
  public void setText(final String text) {
    myText = text;
  }

  @Override
  public String getText() {
    return myText;
  }

  @Override
  public void setText2(final String text) {
    myText2 = text;
  }

  @Override
  public String getText2() {
    return myText2;
  }

  @Override
  public double getFraction() {
    return myFraction;
  }

  @Override
  public void setFraction(final double fraction) {
    synchronized (getLock()) {
      if (isIndeterminate()) {
        String message = "This progress indicator is indeterminate, this may lead to visual inconsistency. " +
                         "Please call setIndeterminate(false) before you start progress. " + getClass();
        LOG.info(message, new IllegalStateException());
        setIndeterminate(false);
      }
      myFraction = fraction;
    }
  }

  @Override
  public void pushState() {
    synchronized (getLock()) {
      getStateStack().push(getState());
    }
  }

  private @NotNull State getState() {
    return new State(getText(), getText2(), getFraction(), isIndeterminate());
  }

  private void restoreFrom(@NotNull State state) {
    setText(state.myText);
    setText2(state.myText2);
    setIndeterminate(state.myIndeterminate);
    if (!isIndeterminate()) {
      setFraction(state.myFraction);
    }
  }

  @Override
  public void popState() {
    synchronized (getLock()) {
      State state = myStateStack.pop();
      restoreFrom(state);
    }
  }

  @Override
  public void startNonCancelableSection() {
    PluginException.reportDeprecatedUsage("ProgressIndicator#startNonCancelableSection", "Use `ProgressManager.executeNonCancelableSection()` instead");
    myNonCancelableSectionCount++;
  }

  @Override
  public void finishNonCancelableSection() {
    myNonCancelableSectionCount--;
  }

  protected boolean isCancelable() {
    return myNonCancelableSectionCount == 0 && !ProgressManager.getInstance().isInNonCancelableSection();
  }

  @Override
  public final boolean isModal() {
    return myModalityProgress != null;
  }

  final boolean isModalEntity() {
    return myModalityProgress == this;
  }

  @Override
  public @NotNull ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public void setModalityProgress(@Nullable ProgressIndicator modalityProgress) {
    if (isRunning()) {
      throwInvalidState("setModalityProgress() must not be called on already running indicator");
    }
    myModalityProgress = modalityProgress;
    setModalityState(modalityProgress);
  }

  private void setModalityState(@Nullable ProgressIndicator modalityProgress) {
    ModalityState modalityState = ModalityState.defaultModalityState();

    if (modalityProgress != null) {
      modalityState = ((ModalityStateEx)modalityState).appendProgress(modalityProgress);
      ((TransactionGuardImpl)TransactionGuard.getInstance()).enteredModality(modalityState);
    }

    myModalityState = modalityState;
  }

  @Override
  public boolean isIndeterminate() {
    return myIndeterminate;
  }

  @Override
  public void setIndeterminate(final boolean indeterminate) {
    // avoid race with popState()
    synchronized (getLock()) {
      myIndeterminate = indeterminate;

      if (indeterminate && getFraction() != 0) {
        myFraction = 0;
      }
    }
  }

  @Override
  public String toString() {
    return "ProgressIndicator " + System.identityHashCode(this) + ": running=" + isRunning() + "; canceled=" + isCanceled();
  }

  @Override
  public boolean isPopupWasShown() {
    return true;
  }

  @Override
  public boolean isShowing() {
    return isModal();
  }

  public void initStateFrom(@NotNull ProgressIndicator indicator) {
    synchronized (getLock()) {
      myRunning = indicator.isRunning();
      myCanceled = indicator.isCanceled();
      boolean indeterminate = indicator.isIndeterminate();
      setIndeterminate(indeterminate);
      // avoid "This progress indicator is indeterminate blah blah"
      if (!indeterminate || indicator.getFraction() != 0) {
        setFraction(indicator.getFraction());
      }
      setText(indicator.getText());
      setText2(indicator.getText2());

      if (indicator instanceof AbstractProgressIndicatorBase) {
        AbstractProgressIndicatorBase stacked = (AbstractProgressIndicatorBase)indicator;
        myStateStack = stacked.myStateStack == null ? null : new Stack<>(stacked.getStateStack());
      }
      dontStartActivity();
    }
  }

  protected void dontStartActivity() {
    myShouldStartActivity = false;
  }

  private @NotNull Stack<State> getStateStack() {
    Stack<State> stack = myStateStack;
    if (stack == null) myStateStack = stack = new Stack<>(2);
    return stack;
  }

  protected @NotNull Object getLock() {
    return lock;
  }
}
