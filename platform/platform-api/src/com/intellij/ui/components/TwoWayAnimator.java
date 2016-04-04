/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.ui.components;

import com.intellij.util.Alarm;
import com.intellij.util.ui.Animator;

/**
 * @author Sergey.Malenkov
 */
abstract class TwoWayAnimator {
  private final Alarm myAlarm = new Alarm();
  private final MyAnimator myForwardAnimator;
  private final MyAnimator myBackwardAnimator;

  private final int myMaxFrame;
  private int myFrame;

  abstract void onFrame(int frame, int maxFrame);

  TwoWayAnimator(String name, int totalFrames, int cycleDuration, int pauseForward, int pauseBackward) {
    myMaxFrame = totalFrames - 1;
    myForwardAnimator = new MyAnimator(name + "ForwardAnimator", totalFrames, cycleDuration, pauseForward, true);
    myBackwardAnimator = new MyAnimator(name + "BackwardAnimator", totalFrames, cycleDuration, pauseBackward, false);
  }

  void startForward() {
    stop();
    myForwardAnimator.start();
  }

  void startBackward() {
    stop();
    myBackwardAnimator.start();
  }

  private void stop() {
    myAlarm.cancelAllRequests();
    myForwardAnimator.suspend();
    myBackwardAnimator.suspend();
  }

  private final class MyAnimator extends Animator implements Runnable {
    private final int myPause;

    private MyAnimator(String name, int totalFrames, int cycleDuration, int pause, boolean forward) {
      super(name, totalFrames, cycleDuration, false, forward);
      myPause = pause;
    }

    private void start() {
      if (isForward() ? myFrame > 0 : myFrame < myMaxFrame) {
        run();
      }
      else {
        myAlarm.addRequest(this, myPause);
      }
    }

    @Override
    public void run() {
      reset();
      resume();
    }

    @Override
    public void paintNow(int frame, int totalFrames, int cycle) {
      if (isForward() ? (frame > myFrame) : (frame < myFrame)) {
        myFrame = frame;
        onFrame(myFrame, myMaxFrame);
      }
    }
  }
}
