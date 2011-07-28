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

package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;

public abstract class Animator implements Disposable {
  private int myTotalFrames;
  private int myCycleLength;
  private final Timer myTimer;

  private int myCurrentFrame;
  private int myQueuedFrames = 0;

  private final boolean myRepeatable;

  private int myRepeatCount;

  private boolean myLastAnimated;

  private boolean myForward = true;

  public Animator(@NonNls final String name,
                  final int totalFrames,
                  final int cycleLength,
                  boolean repeatable,
                  final int interCycleGap,
                  final int maxRepeatCount) {

    this(name, totalFrames, cycleLength, repeatable, interCycleGap, maxRepeatCount, true);
  }

  public Animator(@NonNls final String name,
                  final int totalFrames,
                  final int cycleLength,
                  boolean repeatable,
                  final int interCycleGap,
                  final int maxRepeatCount, boolean forward) {
    myTotalFrames = totalFrames;
    myCycleLength = cycleLength;
    myRepeatable = repeatable;
    myForward = forward;
    myCurrentFrame = forward ? 0 : totalFrames;

    Application application = ApplicationManager.getApplication();

    if (application == null || !application.isUnitTestMode()) {
      myTimer = new Timer(name, myCycleLength / myTotalFrames) {
      protected void onTimer() throws InterruptedException {
        boolean repaint = true;
        if (!isAnimated()) {
          if (myLastAnimated) {
            myCurrentFrame = myForward ? 0 : myTotalFrames;
            myQueuedFrames = 0;
            myLastAnimated = false;
          }
          else {
            repaint = false;
          }
        }
        else {
          myLastAnimated = true;

          if (myQueuedFrames > myTotalFrames) {
            return;
          }

          boolean toNextFrame = myForward ? myCurrentFrame + 1 < myTotalFrames : myCurrentFrame - 1 >= 0;

          if (toNextFrame && myForward) {
            myCurrentFrame++;
          } else if (toNextFrame && !myForward) {
            myCurrentFrame--;
          } else {
            if (myRepeatable) {
              if (maxRepeatCount == -1 || myRepeatCount < maxRepeatCount) {
                myRepeatCount++;
                myCurrentFrame = 0;
                if (interCycleGap > 0) {
                  delay(interCycleGap - getSpan());
                }
              }
              else {
                repaint = false;
                suspend();
                myRepeatCount = 0;
                cycleEnd();
              }
            }
            else {
              repaint = false;
              suspend();
              cycleEnd();
            }
          }
        }

        if (repaint) {
          myQueuedFrames++;
          // paint to EDT
          //noinspection SSBasedInspection
          SwingUtilities.invokeLater(new FramePainter(myCurrentFrame, myTotalFrames, myCycleLength) {
            @Override
            protected void paint(int frame, int totalFrames, int cycleLength) {
              if (isDisposed()) return;
              myQueuedFrames--;
              paintNow(frame, (float)totalFrames, (float)cycleLength);
            }
          });
        }
      }
    };
    }

    if (application == null) {
      try {
        cycleEnd();
      }
      catch (InterruptedException ignored) {
      }
    }
  }

  private static abstract class FramePainter implements Runnable {
    private int myFrameToPaint;
    private int myFrame;
    private int myCycle;

    private FramePainter(int frameToPaint, int totalFrames, int cycleLength) {
      myFrameToPaint = frameToPaint;
      myFrame = totalFrames;
      myCycle = cycleLength;
    }

    @Override
    public final void run() {
      paint(myFrameToPaint, myFrame, myCycle);
    }

    protected abstract void paint(int frame, int totalFrames, int cycleLength);
  }

  @SuppressWarnings({"SSBasedInspection"})
  // paint to EDT
  private void cycleEnd() throws InterruptedException {
    SwingUtilities.invokeLater(new Runnable() {
      public void run() {
        paintCycleEnd();
      }
    });
    onAnimationMaxCycleReached();
  }

  protected void paintCycleEnd() {

  }

  protected void onAnimationMaxCycleReached() throws InterruptedException {

  }

  public void suspend() {
    if (myTimer != null) {
      myTimer.suspend();
    }
  }

  public void resume() {
    if (myTimer != null) { myTimer.resume();}
  }

  public void setTakInitialDelay(boolean take) {
    if (myTimer != null) {myTimer.setTakeInitialDelay(take);}
  }

  public abstract void paintNow(float frame, final float totalFrames, final float cycle);

  public void dispose() {
    if (myTimer != null) {myTimer.dispose();}
  }

  public boolean isRunning() {
    return myTimer != null && myTimer.isRunning() && myLastAnimated;
  }

  public boolean isAnimated() {
    return true;
  }

  public void reset() {
    myCurrentFrame = 0;
    myRepeatCount = 0;
  }

  public final boolean isForward() {
    return myForward;
  }

  public boolean isDisposed() {
    return myTimer == null || myTimer.isDisposed();
  }
}
