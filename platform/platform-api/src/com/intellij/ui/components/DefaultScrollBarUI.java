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

import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.components.JBScrollPane.Alignment;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * @author Sergey.Malenkov
 */
final class DefaultScrollBarUI extends AbstractScrollBarUI {
  private static final JBColor THUMB_BACKGROUND = new JBColor(0x808080, 0x808080);
  private static final JBColor THUMB_FOREGROUND = new JBColor(0x6E6E6E, 0x949494);

  private float myTrackValue;
  private float myThumbValue;

  @Override
  int getThickness() {
    return scale(14);
  }

  @Override
  int getMinimalThickness() {
    return scale(10);
  }

  @Override
  void onTrackHover(boolean hover) {
    if (hover) {
      myTrackAnimator.startForward();
    }
    else {
      myTrackAnimator.startBackward();
    }
  }

  @Override
  void onThumbHover(boolean hover) {
    if (hover) {
      myThumbAnimator.startForward();
    }
    else {
      myThumbAnimator.startBackward();
    }
  }

  @Override
  void paintTrack(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    Composite old = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .1f * myTrackValue));
    g.setColor(Gray.x80);
    g.fillRect(x, y, width, height);
    g.setComposite(old);
  }

  @Override
  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    Composite old = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .4f + .2f * myThumbValue));
    if (c.isOpaque()) {
      x += 1;
      y += 1;
      width -= 2;
      height -= 2;
    }
    else {
      Alignment alignment = Alignment.get(c);
      if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int offset = width - getMinimalThickness();
        if (offset > 0) {
          offset *= 1 - myThumbValue;
          width -= offset;
          if (alignment == Alignment.RIGHT) x += offset;
        }
      }
      else {
        int offset = height - getMinimalThickness();
        if (offset > 0) {
          offset *= 1 - myThumbValue;
          height -= offset;
          if (alignment == Alignment.BOTTOM) y += offset;
        }
      }
    }
    g.setColor(THUMB_BACKGROUND);
    g.fillRect(x + 1, y + 1, width - 2, height - 2);
    g.setColor(THUMB_FOREGROUND);
    g.drawRect(x, y, width - 1, height - 1);
    g.setComposite(old);
  }

  private TwoWayAnimator myTrackAnimator = new TwoWayAnimator("ScrollBarTrack", 6, 300, 300, 1000) {
    @Override
    void onFrame(int frame, int maxFrame) {
      myTrackValue = (float)frame / maxFrame;
      setTrackVisible(frame > 0);
      repaint();
    }
  };

  private TwoWayAnimator myThumbAnimator = new TwoWayAnimator("ScrollBarThumb", 5, 125, 300, 1000) {
    @Override
    void onFrame(int frame, int maxFrame) {
      myThumbValue = (float)frame / maxFrame;
      repaint();
    }
  };
}
