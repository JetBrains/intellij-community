/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.codeInsight.lookup.impl;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.util.ui.Animator;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author peter
 */
public class Advertiser implements Disposable {
  private static final int ourScrollingResolution = 15;
  private final List<String> myTexts = new CopyOnWriteArrayList<String>();
  private final JPanel myComponent = new JPanel() {

    @Override
    public Dimension getPreferredSize() {
      List<String> texts = getTexts();
      if (texts.isEmpty()) return new Dimension(0, 0);

      FontMetrics fm = getFontMetrics(adFont());
      int w = 0;
      for (String text : texts) {
        w = Math.max(w, fm.stringWidth(text));
      }

      Insets insets = getBorder().getBorderInsets(this);
      return new Dimension(w + insets.left + insets.right, fm.getHeight() + insets.top + insets.bottom);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);

      List<String> texts = getTexts();
      if (texts.isEmpty()) return;

      Font font = adFont();
      FontMetrics metrics = g.getFontMetrics(font);
      g.setFont(font);

      int height = getHeight();
      int y = (height - metrics.getHeight()) / 2 + metrics.getAscent() - (myScrollingOffset * height / ourScrollingResolution);
      int x = getBorder().getBorderInsets(this).left;

      if (myCurrentItem >= 0) {
        g.drawString(texts.get(myCurrentItem), x, y);
      }
      if (myScrollingOffset != 0) {
        g.drawString(texts.get((myCurrentItem + 1) % texts.size()), x, y + height);
      }
    }
  };
  private int myCurrentItem = 0;
  private int myScrollingOffset = 0;

  public Advertiser(Disposable parentDisposable) {
    Disposer.register(parentDisposable, this);
  }

  private synchronized List<String> getTexts() {
    return new ArrayList<String>(myTexts);
  }

  public synchronized void clearAdvertisements() {
    myTexts.clear();
    myCurrentItem = 0;
    myScrollingOffset = 0;
  }

  private static Font adFont() {
    Font font = UIUtil.getLabelFont();
    return font.deriveFont((float)(font.getSize() - 2));
  }

  public synchronized void addAdvertisement(@NotNull String text) {
    myTexts.add(text);
    if (myTexts.size() == 2) {
      if (!myComponent.isShowing()) {
        myCurrentItem = -1;
      }
      int interCycleGap = 4000;
      Animator animator = new Animator("completion ad", ourScrollingResolution, 800, true, interCycleGap, -1) {
        @Override
        public void paintNow(float frame, float totalFrames, float cycle) {
          myScrollingOffset = (int)frame;
          if (myScrollingOffset == 0) {
            myCurrentItem = (myCurrentItem + 1) % getTexts().size();
          }
          myComponent.paintImmediately(0, 0, myComponent.getWidth(), myComponent.getHeight());
        }
      };
      animator.resume();
      Disposer.register(this, animator);
    }
  }

  public JComponent getAdComponent() {
    return myComponent;
  }

  @Override
  public void dispose() {
  }
}
