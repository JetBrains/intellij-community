/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

import javax.swing.*;

public abstract class Animator implements Disposable {

  private String myName;
  private int myTotalFrames;
  private int myCycleLength;
  private Timer myTimer;

  private int myCurrentFrame = 0;
  private int myQueuedFrames = 0;

  private final boolean myRepeatable;

  private int myRepeatCount;

  private boolean myLastAnimated;

  public Animator(final String name,
                  final int totalFrames,
                  final int cycleLength,
                  boolean repeatable,
                  final int interCycleGap,
                  final int maxRepeatCount) {
    myName = name;
    myTotalFrames = totalFrames;
    myCycleLength = cycleLength;
    myRepeatable = repeatable;

    myTimer = new Timer(myName, myCycleLength / myTotalFrames) {
      protected void onTimer() throws InterruptedException {
        boolean repaint = true;
        if (!isAnimated()) {
          if (myLastAnimated) {
            myCurrentFrame = 0;
            myQueuedFrames = 0;
            myLastAnimated = false;
          }
          else {
            repaint = false;
          }
        }
        else {
          myLastAnimated = true;

          if (myQueuedFrames > myTotalFrames) return;

          if (myCurrentFrame + 1 < myTotalFrames) {
            myCurrentFrame++;
          }
          else {
            if (myRepeatable) {
              if (maxRepeatCount == -1 || myRepeatCount < maxRepeatCount) {
                myRepeatCount++;
                myCurrentFrame = 0;
                if (interCycleGap > 0) {
                  Thread.sleep(interCycleGap - getSpan());
                }
              }
              else {
                repaint = false;
                suspend();
                myRepeatCount = 0;
                onAnimationMaxCycleReached();
              }
            }
            else {
              repaint = false;
              suspend();
              onAnimationMaxCycleReached();
            }
          }
        }

        if (repaint) {
          myQueuedFrames++;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myQueuedFrames--;
              paintNow(myCurrentFrame, (float)myTotalFrames, (float)myCycleLength);
            }
          });
        }
      }
    };
  }

  protected void onAnimationMaxCycleReached() throws InterruptedException {

  }

  public void suspend() {
    myTimer.suspend();
  }

  public void resume() {
    myTimer.resume();
  }

  public void setTakInitialDelay(boolean take) {
    myTimer.setTakeInitialDelay(take);
  }

  public abstract void paintNow(float frame, final float totalFrames, final float cycle);

  public void dispose() {
    myTimer.dispose();
  }

  public boolean isRunning() {
    return myTimer.isRunning() && myLastAnimated;
  }

  public boolean isAnimated() {
    return true;
  }

  public void reset() {
    myCurrentFrame = 0;
    myRepeatCount = 0;
  }

  public boolean isDisposed() {
    return myTimer.isDisposed();
  }
}
