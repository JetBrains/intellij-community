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
import com.intellij.ui.Gray;
import com.intellij.ui.components.JBScrollPane.Alignment;
import com.intellij.util.ui.UIUtil;

import java.awt.AlphaComposite;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.Rectangle;
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
    Rectangle bounds = getAnimatedBounds(x, y, width, height, c, false);
    Composite old = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .1f * myTrackValue));
    g.setColor(Gray.x80);
    g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    g.setComposite(old);
  }

  @Override
  void paintThumb(Graphics2D g, int x, int y, int width, int height, JComponent c) {
    Rectangle bounds = getAnimatedBounds(x, y, width, height, c, Registry.is("ide.scroll.thumb.small.if.opaque"));
    Composite old = g.getComposite();
    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .35f + .25f * myThumbValue));
    g.setColor(Gray.x80);
    g.fillRect(bounds.x + 1, bounds.y + 1, bounds.width - 2, bounds.height - 2);
    g.setColor(UIUtil.isUnderDarcula() ? Gray.x94 : Gray.x6E);
    if (Registry.is("ide.scroll.thumb.border.rounded")) {
      g.drawLine(bounds.x + 1, bounds.y, bounds.x + bounds.width - 2, bounds.y);
      g.drawLine(bounds.x + 1, bounds.y + bounds.height - 1, bounds.x + bounds.width - 2, bounds.y + bounds.height - 1);
      g.drawLine(bounds.x, bounds.y + 1, bounds.x, bounds.y + bounds.height - 2);
      g.drawLine(bounds.x + bounds.width - 1, bounds.y + 1, bounds.x + bounds.width - 1, bounds.y + bounds.height - 2);
    }
    else {
      g.drawRect(bounds.x, bounds.y, bounds.width - 1, bounds.height - 1);
    }
    g.setComposite(old);
  }

  private Rectangle getAnimatedBounds(int x, int y, int width, int height, JComponent c, boolean small) {
    Rectangle bounds = new Rectangle(x, y, width, height);
    if (!c.isOpaque()) {
      Alignment alignment = Alignment.get(c);
      if (alignment == Alignment.LEFT || alignment == Alignment.RIGHT) {
        int value = getAnimatedValue(width - getMinimalThickness());
        if (value > 0) {
          bounds.width -= value;
          if (alignment == Alignment.RIGHT) bounds.x += value;
        }
      }
      else {
        int value = getAnimatedValue(height - getMinimalThickness());
        if (value > 0) {
          bounds.height -= value;
          if (alignment == Alignment.BOTTOM) bounds.y += value;
        }
      }
    }
    else if (small) {
      bounds.x += 1;
      bounds.y += 1;
      bounds.width -= 2;
      bounds.height -= 2;
    }
    return bounds;
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
