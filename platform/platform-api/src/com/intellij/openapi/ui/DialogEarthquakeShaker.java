// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.Animator;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DialogEarthquakeShaker {
  private final Window myWindow;
  private Point myNaturalLocation;
  private long myStartTime;

  private DialogEarthquakeShaker(Window window) {
    myWindow = window;
  }

  public void startShake() {
    myNaturalLocation = myWindow.getLocation();
    myStartTime = System.currentTimeMillis();
    new Animator("EarthQuake", 10, 70, true) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        final long elapsed = System.currentTimeMillis() - myStartTime;
        final double waveOffset = (elapsed % 70) / 70d;
        final double angle = waveOffset * 2d * Math.PI;
        final int shakenX = (int)((Math.sin(angle) * 10) + myNaturalLocation.x);
        myWindow.setLocation(shakenX, myNaturalLocation.y);
        myWindow.repaint();
        if (elapsed > 150) {
          suspend();
          myWindow.setLocation(myNaturalLocation);
          myWindow.repaint();
          Disposer.dispose(this);
        }
      }
    }.resume();
  }

  public static void shake(Window window) {
    new DialogEarthquakeShaker(window).startShake();
  }
}
