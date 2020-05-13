// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.components;

import com.intellij.util.Alarm;
import com.intellij.util.ui.Animator;

abstract class TwoWayAnimator {
  private final Alarm myAlarm = new Alarm();
  private final MyAnimator myForwardAnimator;
  private final MyAnimator myBackwardAnimator;

  private final int myMaxFrame;
  private int myFrame;
  float myValue;

  abstract void onValueUpdate();

  TwoWayAnimator(String name, int totalFrames, int pauseForward, int durationForward, int pauseBackward, int durationBackward) {
    myMaxFrame = totalFrames - 1;
    myForwardAnimator = new MyAnimator(name + "ForwardAnimator", totalFrames, durationForward, pauseForward, true);
    myBackwardAnimator = new MyAnimator(name + "BackwardAnimator", totalFrames, durationBackward, pauseBackward, false);
  }

  void start(boolean forward) {
    stop();
    MyAnimator animator = forward ? myForwardAnimator : myBackwardAnimator;
    if (!forward ? myFrame > 0 : myFrame < myMaxFrame) {
      if (forward ? myFrame > 0 : myFrame < myMaxFrame) {
        animator.run();
      }
      else {
        myAlarm.addRequest(animator, animator.myPause);
      }
    }
  }

  void rewind(boolean forward) {
    stop();
    if (forward) {
      if (myFrame != myMaxFrame) setFrame(myMaxFrame);
    }
    else {
      if (myFrame != 0) setFrame(0);
    }
  }

  void stop() {
    myAlarm.cancelAllRequests();
    myForwardAnimator.suspend();
    myBackwardAnimator.suspend();
  }

  void setFrame(int frame) {
    myFrame = frame;
    myValue = frame == 0 ? 0 : frame == myMaxFrame ? 1 : (float)frame / myMaxFrame;
    onValueUpdate();
  }

  private final class MyAnimator extends Animator implements Runnable {
    private final int myPause;

    private MyAnimator(String name, int totalFrames, int cycleDuration, int pause, boolean forward) {
      super(name, totalFrames, cycleDuration, false, forward);
      myPause = pause;
    }

    @Override
    public void run() {
      reset();
      resume();
    }

    @Override
    public void paintNow(int frame, int totalFrames, int cycle) {
      if (isForward() ? (frame > myFrame) : (frame < myFrame)) {
        setFrame(frame);
      }
    }

    @Override
    protected void paintCycleEnd() {
      setFrame(isForward() ? myMaxFrame : 0);
    }
  }
}
