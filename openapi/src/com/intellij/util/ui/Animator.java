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

  public Animator(final String name, final int totalFrames, final int cycleLength, boolean repeatable) {
    myName = name;
    myTotalFrames = totalFrames;
    myCycleLength = cycleLength;
    myRepeatable = repeatable;

    myTimer = new Timer(myName, myCycleLength / myTotalFrames) {
      protected void onTimer() {
        if (myQueuedFrames > myTotalFrames) return;

        boolean repaint = true;
        if (myCurrentFrame + 1 < myTotalFrames) {
          myCurrentFrame++;
        }
        else {
          if (myRepeatable) {
            myCurrentFrame = 0;
          }
          else {
            repaint = false;
            suspend();
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
    return myTimer.isRunning();
  }
}
