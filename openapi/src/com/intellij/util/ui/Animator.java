package com.intellij.util.ui;

import javax.swing.*;

public abstract class Animator {

  private String myName;
  private int myTotalFrames;
  private int myCycleLength;
  private Timer myTimer;

  private int myCurrentFrame = 0;
  private int myQueuedFrames = 0;

  private boolean myRepeatable;

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
        } else {
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
            }
          }
        }

        if (repaint) {
          myQueuedFrames++;
          SwingUtilities.invokeLater(new Runnable() {
            public void run() {
              myQueuedFrames--;
              paintNow(myCurrentFrame);
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

  public abstract void paintNow(int frame);

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
