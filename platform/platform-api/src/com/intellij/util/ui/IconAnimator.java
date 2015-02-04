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
package com.intellij.util.ui;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public class IconAnimator extends Animator implements Icon {
  private final Icon myBase;
  @NotNull private final PaintCallback myCallback;
  private final Color[] myAlphas;
  private int myFrame;
  private boolean myActive;

  public IconAnimator(@NotNull Disposable parent, @NotNull Icon base, @NotNull PaintCallback callback) {
    super("IconAnimator{" + System.currentTimeMillis()+"}", base.getIconWidth(), 2000, true);
    Disposer.register(parent, this);
    myBase = base;
    myCallback = callback;
    myAlphas = initAlphas();
  }

  private Color[] initAlphas() {
    Color[] colors = new Color[myBase.getIconWidth()];
    for (int i = 0; i < colors.length; i++) {
      double a = 2 * Math.PI * i / colors.length;
      float v = (1 - (float)Math.sin(a)) / 2;
      //noinspection UseJBColor
      colors[i] = new Color(v, v, v, v * v * v / 16 + .05F);
    }
    return colors;
  }

  public void setActive(boolean active) {
    if (myActive == active) return;

    myActive = active;

    if (isRunning() ^ myActive) {
      if (myActive) {
        resume();
      } else {
        suspend();
      }
      myCallback.paintNow(this);
    }
  }

  @Override
  public void paintNow(int frame, int totalFrames, int cycle) {
    myFrame = frame;
    myCallback.paintNow(this);
  }

  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y) {
    myBase.paintIcon(component, graphics, x, y);
    if (!myActive) return;

    for (int i = 0; i < myAlphas.length; i++) {
      Color alpha = myAlphas[(i + myFrame) % myAlphas.length];
      graphics.setColor(alpha);
      graphics.drawRect(x + i, y, 1, getIconHeight());
    }
  }

  @Override
  public int getIconWidth() {
    return myBase.getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return myBase.getIconHeight();
  }

  public interface PaintCallback {
    void paintNow(Icon icon);
  }
}
