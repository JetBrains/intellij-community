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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ConcurrencyUtil;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class Animator implements Disposable {
  private static final ScheduledExecutorService scheduler = ConcurrencyUtil.newSingleScheduledThreadExecutor("Animations");

  private final int myTotalFrames;
  private final int myCycleDuration;
  private final boolean myForward;
  private final boolean myRepeatable;

  private ScheduledFuture<?> myTicker;

  private int myCurrentFrame;
  private long myStartTime;
  private long myStopTime;
  private volatile boolean myDisposed = false;

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
    myTotalFrames = totalFrames;
    myCycleDuration = cycleDuration;
    myRepeatable = repeatable;
    myForward = forward;
    myCurrentFrame = forward ? 0 : totalFrames;

    if (ApplicationManager.getApplication() == null) {
      animationDone();
    }
    else {
      reset();
    }
  }
  
  private void onTick() {
    if (isDisposed()) return;
    
    if (myStartTime == -1) {
      myStartTime = System.currentTimeMillis();
      myStopTime = myStartTime + myCycleDuration * (myTotalFrames - myCurrentFrame) / myTotalFrames;
    }

    final double passedTime = System.currentTimeMillis() - myStartTime;
    final double totalTime = myStopTime - myStartTime;
    
    final int newFrame = (int)(passedTime * myTotalFrames / totalTime);
    if (myCurrentFrame > 0 && newFrame == myCurrentFrame) return;
    myCurrentFrame = newFrame;

    if (myCurrentFrame >= myTotalFrames) {
      if (myRepeatable) {
        reset();
      }
      else {
        animationDone();
        return;
      }
    }

    paint();
  }

  private void paint() {
    paintNow(myForward ? myCurrentFrame : myTotalFrames - myCurrentFrame - 1, myTotalFrames, myCycleDuration);
  }

  private void animationDone() {
    stopTicker();

    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        paintCycleEnd();
      }
    });
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
    myStartTime = -1;
    stopTicker();
  }

  public void resume() {
    final Application app = ApplicationManager.getApplication();
    if (app == null || app.isUnitTestMode()) return;

    if (myCycleDuration == 0) {
      myCurrentFrame = myTotalFrames - 1;
      paint();
      animationDone();
    }
    else if (myTicker == null) {
      myTicker = scheduler.scheduleWithFixedDelay(new Runnable() {
        AtomicBoolean scheduled = new AtomicBoolean(false);

        @Override
        public void run() {
          if (scheduled.compareAndSet(false, true) && !isDisposed()) {
            SwingUtilities.invokeLater(new Runnable() {
              @Override
              public void run() {
                scheduled.set(false);
                onTick();
              }
            });
          }
        }
      }, 0, myCycleDuration * 1000 / myTotalFrames, TimeUnit.MICROSECONDS);
    }
  }

  public abstract void paintNow(int frame, int totalFrames, int cycle);

  @Override
  public void dispose() {
    myDisposed = true;
    stopTicker();
  }

  public boolean isRunning() {
    return myTicker != null;
  }

  public void reset() {
    myCurrentFrame = 0;
    myStartTime = -1;
  }

  public final boolean isForward() {
    return myForward;
  }

  public boolean isDisposed() {
    return myDisposed;
  }
}
