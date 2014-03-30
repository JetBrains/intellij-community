/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.ModalityStateEx;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.containers.DoubleArrayList;
import com.intellij.util.containers.Stack;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AbstractProgressIndicatorBase extends UserDataHolderBase implements ProgressIndicatorStacked {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.progress.util.ProgressIndicatorBase");

  private volatile String myText;
  private volatile double myFraction;
  private volatile String myText2;

  private volatile boolean myCanceled;
  private volatile boolean myRunning;
  private volatile boolean myFinished;

  private volatile boolean myIndeterminate;

  private Stack<String> myTextStack;
  private DoubleArrayList myFractionStack;
  private Stack<String> myText2Stack;
  private volatile int myNonCancelableCount;

  protected ProgressIndicator myModalityProgress;
  private volatile ModalityState myModalityState = ModalityState.NON_MODAL;

  @Override
  public synchronized void start() {
    LOG.assertTrue(!isRunning(), "Attempt to start ProgressIndicator which is already running");
    if (myFinished) {
      if (myCanceled && !isReuseable()) {
        if (ourReportedReuseExceptions.add(getClass())) {
          LOG.error("Attempt to start ProgressIndicator which is cancelled and already stopped:" + this + "," + getClass());
        }
      }
      myCanceled = false;
      myFinished = false;
    }

    myText = "";
    myFraction = 0;
    myText2 = "";
    myRunning = true;
  }

  private static final ConcurrentHashSet<Class> ourReportedReuseExceptions = new ConcurrentHashSet<Class>(2);

  protected boolean isReuseable() {
    return false;
  }

  @Override
  public synchronized void stop() {
    LOG.assertTrue(myRunning, "stop() should be called only if start() called before");
    myRunning = false;
    myFinished = true;
  }

  @Override
  public boolean isRunning() {
    return myRunning;
  }

  @Override
  public void cancel() {
    myCanceled = true;
  }

  @Override
  public boolean isCanceled() {
    return myCanceled;
  }

  @Override
  public void checkCanceled() {
    if (isCanceled() && isCancelable()) {
      throw new ProcessCanceledException();
    }
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
    myFraction = fraction;
  }

  @Override
  public synchronized void pushState() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    myTextStack.push(myText);
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    myFractionStack.add(myFraction);
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    myText2Stack.push(myText2);
  }

  @Override
  public synchronized void popState() {
    LOG.assertTrue(!myTextStack.isEmpty());
    String oldText = myTextStack.pop();
    double oldFraction = myFractionStack.remove(myFractionStack.size() - 1);
    String oldText2 = myText2Stack.pop();
    setText(oldText);
    setFraction(oldFraction);
    setText2(oldText2);
  }

  @Override
  public void startNonCancelableSection() {
    myNonCancelableCount++;
  }

  @Override
  public void finishNonCancelableSection() {
    myNonCancelableCount--;
  }

  protected boolean isCancelable() {
    return myNonCancelableCount == 0;
  }

  @Override
  public final boolean isModal() {
    return myModalityProgress != null;
  }

  @Override
  @NotNull
  public final ModalityState getModalityState() {
    return myModalityState;
  }

  @Override
  public void setModalityProgress(ProgressIndicator modalityProgress) {
    LOG.assertTrue(!isRunning());
    myModalityProgress = modalityProgress;
    ModalityState currentModality = ApplicationManager.getApplication().getCurrentModalityState();
    myModalityState = myModalityProgress != null ? ((ModalityStateEx)currentModality).appendProgress(myModalityProgress) : currentModality;
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

  @Override
  public synchronized void initStateFrom(@NotNull final ProgressIndicator indicator) {
    myRunning = indicator.isRunning();
    myCanceled = indicator.isCanceled();
    myFraction = indicator.getFraction();
    myIndeterminate = indicator.isIndeterminate();
    myText = indicator.getText();

    myText2 = indicator.getText2();

    myFraction = indicator.getFraction();

    if (indicator instanceof ProgressIndicatorStacked) {
      ProgressIndicatorStacked stacked = (ProgressIndicatorStacked)indicator;

      myNonCancelableCount = stacked.getNonCancelableCount();

      myTextStack = new Stack<String>(stacked.getTextStack());

      myText2Stack = new Stack<String>(stacked.getText2Stack());

      myFractionStack = new DoubleArrayList(stacked.getFractionStack());
    }
  }

  @Override
  @NotNull
  public synchronized Stack<String> getTextStack() {
    if (myTextStack == null) myTextStack = new Stack<String>(2);
    return myTextStack;
  }

  @Override
  @NotNull
  public synchronized DoubleArrayList getFractionStack() {
    if (myFractionStack == null) myFractionStack = new DoubleArrayList(2);
    return myFractionStack;
  }

  @Override
  @NotNull
  public synchronized Stack<String> getText2Stack() {
    if (myText2Stack == null) myText2Stack = new Stack<String>(2);
    return myText2Stack;
  }

  @Override
  public int getNonCancelableCount() {
    return myNonCancelableCount;
  }
}
