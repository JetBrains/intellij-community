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

import com.intellij.openapi.util.registry.Registry;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.ui.RegionPainter;

import java.awt.Graphics2D;
import javax.swing.JComponent;

/**
 * @author Sergey.Malenkov
 */
final class DefaultScrollBarUI extends AbstractScrollBarUI {
  private float myTrackValue;
  private float myThumbValue;

  @Override
  int getThickness() {
    return scale(isOpaque() ? 13 : 14);
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
    RegionPainter<Float> p = isDark(c) ? JBScrollPane.TRACK_DARK_PAINTER : JBScrollPane.TRACK_PAINTER;
    paint(p, g, x, y, width, height, c, myTrackValue, false);
  }

  @Override
  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    RegionPainter<Float> p = isDark(c) ? JBScrollPane.THUMB_DARK_PAINTER : JBScrollPane.THUMB_PAINTER;
    paint(p, g, x, y, width, height, c, myThumbValue, Registry.is("ide.scroll.thumb.small.if.opaque"));
  }

  private void paint(RegionPainter<Float> p, Graphics2D g, int x, int y, int width, int height, JComponent c, float value, boolean small) {
    if (!c.isOpaque()) {
      Alignment alignment = Alignment.get(c);
      if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int offset = getAnimatedValue(width - getMinimalThickness());
        if (offset > 0) {
          width -= offset;
          if (alignment == Alignment.RIGHT) x += offset;
        }
      }
      else {
        int offset = getAnimatedValue(height - getMinimalThickness());
        if (offset > 0) {
          height -= offset;
          if (alignment == Alignment.BOTTOM) y += offset;
        }
      }
    }
    else if (small) {
      x += 1;
      y += 1;
      width -= 2;
      height -= 2;
    }
    p.paint(g, x, y, width, height, value);
  }

  private int getAnimatedValue(int value) {
    if (!Registry.is("ide.scroll.bar.expand.animation")) return value;
    if (myTrackValue <= 0) return value;
    if (myTrackValue >= 1) return 0;
    return (int)(.5f + value * (1 - myTrackValue));
  }

  private TwoWayAnimator myTrackAnimator = new TwoWayAnimator("ScrollBarTrack", 6, 125, 150, 300) {
    @Override
    void onFrame(int frame, int maxFrame) {
      myTrackValue = (float)frame / maxFrame;
      setTrackVisible(frame > 0);
      repaint();
    }
  };

  private TwoWayAnimator myThumbAnimator = new TwoWayAnimator("ScrollBarThumb", 6, 125, 150, 300) {
    @Override
    void onFrame(int frame, int maxFrame) {
      myThumbValue = (float)frame / maxFrame;
      repaint();
    }
  };
}
