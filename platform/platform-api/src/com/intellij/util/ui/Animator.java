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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.concurrency.EdtExecutorService;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public abstract class Animator implements Disposable {
  private final String myName;
  private final int myTotalFrames;
  private final int myCycleDuration;
  private final boolean myForward;
  private final boolean myRepeatable;

  private ScheduledFuture<?> myTicker;

  private int myCurrentFrame;
  private long myStartTime;
  private long myStartDeltaTime;
  private boolean myInitialStep;
  private volatile boolean myDisposed;

  public Animator(@NonNls final String name,
                  final int totalFrames,
                  final int cycleDuration,
                  boolean repeatable) {
    this(name, totalFrames, cycleDuration, repeatable, true);
  }

  public Animator(@NonNls final String name,
                  final int totalFrames,
                  final int cycleDuration,
                  boolean repeatable,
                  boolean forward) {
    myName = name;
    myTotalFrames = totalFrames;
    myCycleDuration = cycleDuration;
    myRepeatable = repeatable;
    myForward = forward;

    reset();

    if (skipAnimation()) {
      animationDone();
    }
  }

  private void onTick() {
    if (isDisposed()) return;

    if (myInitialStep) {
      myInitialStep = false;
      myStartTime = System.currentTimeMillis() - myStartDeltaTime; // keep animation state on suspend
      paint();
      return;
    }

    double cycleTime = System.currentTimeMillis() - myStartTime;
    if (cycleTime < 0) return; // currentTimeMillis() is not monotonic - let's pretend that animation didn't changed

    long newFrame = (long)(cycleTime * myTotalFrames / myCycleDuration);

    if (myRepeatable) {
      newFrame %= myTotalFrames;
    }

    if (newFrame == myCurrentFrame) return;

    if (!myRepeatable && newFrame >= myTotalFrames) {
      animationDone();
      return;
    }

    myCurrentFrame = (int)newFrame;

    paint();
  }

  private void paint() {
    paintNow(myForward ? myCurrentFrame : myTotalFrames - myCurrentFrame - 1, myTotalFrames, myCycleDuration);
  }

  private void animationDone() {
    stopTicker();

    if (!isDisposed()) {
      SwingUtilities.invokeLater(this::paintCycleEnd);
    }
  }

  private void stopTicker() {
    if (myTicker != null) {
      myTicker.cancel(false);
      myTicker = null;
    }
  }

  protected void paintCycleEnd() {

  }

  public void suspend() {
    myStartDeltaTime = System.currentTimeMillis() - myStartTime;
    myInitialStep = true;
    stopTicker();
  }

  public void resume() {
    if (isDisposed()) {
      stopTicker();
      return;
    }
    if (skipAnimation()) {
      animationDone();
      return;
    }

    if (myCycleDuration == 0) {
      myCurrentFrame = myTotalFrames - 1;
      paint();
      animationDone();
    }
    else if (myTicker == null) {
      myTicker = EdtExecutorService.getScheduledExecutorInstance().scheduleWithFixedDelay(new Runnable() {
        @Override
        public void run() {
          onTick();
        }

        @Override
        public String toString() {
          return "Scheduled "+Animator.this;
        }
      }, 0, myCycleDuration * 1000L / myTotalFrames, TimeUnit.MICROSECONDS);
    }
  }

  private static boolean skipAnimation() {
    if (GraphicsEnvironment.isHeadless()) {
      return true;
    }
    Application app = ApplicationManager.getApplication();
    return app != null && app.isUnitTestMode();
  }

  public abstract void paintNow(int frame, int totalFrames, int cycle);

  @Override
  public void dispose() {
    stopTicker();
    myDisposed = true;
  }

  public boolean isRunning() {
    return myTicker != null;
  }

  public void reset() {
    myCurrentFrame = 0;
    myStartDeltaTime = 0;
    myInitialStep = true;
  }

  public final boolean isForward() {
    return myForward;
  }

  public boolean isDisposed() {
    return myDisposed;
  }

  @Override
  public String toString() {
    ScheduledFuture<?> future = myTicker;
    return "Animator '"+myName+"' @" + System.identityHashCode(this) +
           (future == null || future.isDone() ? " (stopped)": " (running "+myCurrentFrame+"/"+myTotalFrames +" frame)");
  }
}
