// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.progress.util;

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
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.mac.foundation.MacUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Stack;
import gnu.trove.TDoubleArrayList;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;

public class AbstractProgressIndicatorBase extends UserDataHolderBase implements ProgressIndicator {
  private static final Logger LOG = Logger.getInstance(AbstractProgressIndicatorBase.class);

  private volatile String myText;
  private volatile double myFraction;
  private volatile String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;
  private volatile boolean myStopped;

  private volatile boolean myIndeterminate = Registry.is("ide.progress.indeterminate.by.default", true);
  private volatile MacUtil.Activity myMacActivity;
  private volatile boolean myShouldStartActivity = true;

  private Stack<String> myTextStack; // guarded by this
  private TDoubleArrayList myFractionStack; // guarded by this
  private Stack<String> myText2Stack; // guarded by this

  private ProgressIndicator myModalityProgress;
  private volatile ModalityState myModalityState = ModalityState.NON_MODAL;
  private volatile int myNonCancelableSectionCount;
  private final Object lock = ObjectUtils.sentinel("APIB lock");

  @Override
  public void start() {
    synchronized (getLock()) {
      LOG.assertTrue(!isRunning(), "Attempt to start ProgressIndicator which is already running");
      if (myStopped) {
        if (myCanceled && !isReuseable()) {
          if (ourReportedReuseExceptions.add(getClass())) {
            LOG.error("Attempt to start ProgressIndicator which is cancelled and already stopped:" + this + "," + getClass());
          }
        }
        myCanceled = false;
        myStopped = false;
      }

      myText = "";
      myFraction = 0;
      myText2 = "";
      startSystemActivity();
      myRunning = true;
    }
  }

  private static final Set<Class<?>> ourReportedReuseExceptions = ContainerUtil.newConcurrentSet();

  protected boolean isReuseable() {
    return false;
  }

  @Override
  public void stop() {
    synchronized (getLock()) {
      LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
      myRunning = false;
      myStopped = true;
      stopSystemActivity();
    }
  }

  private void startSystemActivity() {
    myMacActivity = myShouldStartActivity ? MacUtil.wakeUpNeo(this) : null;
  }

  void stopSystemActivity() {
    MacUtil.Activity macActivity = myMacActivity;
    if (macActivity != null) {
      macActivity.matrixHasYou();
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

  @Nullable
  protected Throwable getCancellationTrace() {
    if (this instanceof Disposable) {
      return Disposer.getDisposalTrace((Disposable)this);
    }
    return null;
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
    if (isIndeterminate()) {
      StackTraceElement[] trace = new Throwable().getStackTrace();
      Optional<StackTraceElement> first = Arrays.stream(trace)
        .filter(element -> !element.getClassName().startsWith("com.intellij.openapi.progress.util"))
        .findFirst();
      String message = "This progress indicator is indeterminate, this may lead to visual inconsistency. " +
                       "Please call setIndeterminate(false) before you start progress.";
      if (first.isPresent()) {
        message += "\n" + first.get();
      }
      LOG.warn(message);
      setIndeterminate(false);
    }
    myFraction = fraction;
  }

  @Override
  public void pushState() {
    synchronized (getLock()) {
      getTextStack().push(myText);
      getFractionStack().add(myFraction);
      getText2Stack().push(myText2);
    }
  }

  @Override
  public void popState() {
    synchronized (getLock()) {
      LOG.assertTrue(!myTextStack.isEmpty());
      String oldText = myTextStack.pop();
      String oldText2 = myText2Stack.pop();
      setText(oldText);
      setText2(oldText2);

      double oldFraction = myFractionStack.remove(myFractionStack.size() - 1);
      if (!isIndeterminate()) {
        setFraction(oldFraction);
      }
    }
  }

  @Override
  public void startNonCancelableSection() {
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
  @NotNull
  public ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public void setModalityProgress(@Nullable ProgressIndicator modalityProgress) {
    LOG.assertTrue(!isRunning());
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
    myIndeterminate = indeterminate;
  }


  @NonNls
  @Override
  public String toString() {
    return "ProgressIndicator " + System.identityHashCode(this) + ": running="+isRunning()+"; canceled="+isCanceled();
  }

  @Override
  public boolean isPopupWasShown() {
    return true;
  }

  @Override
  public boolean isShowing() {
    return isModal();
  }

  public void initStateFrom(@NotNull final ProgressIndicator indicator) {
    synchronized (getLock()) {
      myRunning = indicator.isRunning();
      myCanceled = indicator.isCanceled();
      myFraction = indicator.getFraction();
      myIndeterminate = indicator.isIndeterminate();
      myText = indicator.getText();

      myText2 = indicator.getText2();

      myFraction = indicator.getFraction();

      if (indicator instanceof AbstractProgressIndicatorBase) {
        AbstractProgressIndicatorBase stacked = (AbstractProgressIndicatorBase)indicator;

        myTextStack = stacked.myTextStack == null ? null : new Stack<>(stacked.getTextStack());

        myText2Stack = stacked.myText2Stack == null ? null : new Stack<>(stacked.getText2Stack());

        myFractionStack = stacked.myFractionStack == null ? null : new TDoubleArrayList(stacked.getFractionStack().toNativeArray());
      }
      dontStartActivity();
    }
  }

  protected void dontStartActivity() {
    myShouldStartActivity = false;
  }

  @NotNull
  private Stack<String> getTextStack() {
    Stack<String> stack = myTextStack;
    if (stack == null) myTextStack = stack = new Stack<>(2);
    return stack;
  }

  @NotNull
  private TDoubleArrayList getFractionStack() {
    TDoubleArrayList stack = myFractionStack;
    if (stack == null) myFractionStack = stack = new TDoubleArrayList(2);
    return stack;
  }

  @NotNull
  private Stack<String> getText2Stack() {
    Stack<String> stack = myText2Stack;
    if (stack == null) myText2Stack = stack = new Stack<>(2);
    return stack;
  }

  @NotNull
  protected Object getLock() {
    return lock;
  }
}
