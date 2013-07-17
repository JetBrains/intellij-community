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
package com.intellij.openapi.ui;

import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.Animator;

import javax.swing.*;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class DialogEarthquakeShaker {
  private JDialog myDialog;
  private Point myNaturalLocation;
  private long myStartTime;

  private DialogEarthquakeShaker(JDialog dialog) {
    myDialog = dialog;
  }

  public void startShake() {
    myNaturalLocation = myDialog.getLocation();
    myStartTime = System.currentTimeMillis();
    new Animator("EarthQuake", 10, 70, true) {
      @Override
      public void paintNow(int frame, int totalFrames, int cycle) {
        final long elapsed = System.currentTimeMillis() - myStartTime;
        final double waveOffset = (elapsed % 70) / 70d;
        final double angle = waveOffset * 2d * Math.PI;
        final int shakenX = (int)((Math.sin(angle) * 10) + myNaturalLocation.x);
        myDialog.setLocation(shakenX, myNaturalLocation.y);
        myDialog.repaint();
        if (elapsed > 150) {
          suspend();
          myDialog.setLocation(myNaturalLocation);
          myDialog.repaint();
          Disposer.dispose(this);
        }
      }
    }.resume();
  }

  public static void shake(JDialog dialog) {
    new DialogEarthquakeShaker(dialog).startShake();
  }
}
