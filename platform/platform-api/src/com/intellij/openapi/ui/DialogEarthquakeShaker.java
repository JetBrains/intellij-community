// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui;

import com.intellij.util.ui.Animator;

import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public final class DialogEarthquakeShaker {
  private final Window window;
  private Point naturalLocation;
  private long startTime;

  private DialogEarthquakeShaker(Window window) {
    this.window = window;
  }

  public void startShake() {
    naturalLocation = window.getLocation();
    startTime = System.currentTimeMillis();
    new Animator("EarthQuake", 10, 70, true) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        final long elapsed = System.currentTimeMillis() - startTime;
        final double waveOffset = (elapsed % 70) / 70d;
        final double angle = waveOffset * 2d * Math.PI;
        final int shakenX = (int)((Math.sin(angle) * 10) + naturalLocation.x);
        window.setLocation(shakenX, naturalLocation.y);
        window.repaint();
        if (elapsed > 150) {
          suspend();
          window.setLocation(naturalLocation);
          window.repaint();
          dispose();
        }
      }
    }.resume();
  }

  public static void shake(Window window) {
    new DialogEarthquakeShaker(window).startShake();
  }
}
