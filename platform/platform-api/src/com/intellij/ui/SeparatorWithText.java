/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

public class SeparatorWithText extends JComponent {

  private String myCaption = "";
  private int myPrefWidth;

  public SeparatorWithText() {
    setBorder(BorderFactory.createEmptyBorder(getVgap(), 0, getVgap(), 0));
    setFont(UIUtil.getLabelFont());
    setFont(getFont().deriveFont(Font.BOLD));
  }

  private static int getVgap() {
    return UIUtil.isUnderNativeMacLookAndFeel() ? 1 : 3;
  }

  private static int getHgap() {
    return 3;
  }

  public Dimension getPreferredSize() {
    final Dimension size = getPreferredFontSize();
    size.width = myPrefWidth == -1? size.width : myPrefWidth;
    return size;
  }

  public Dimension getPreferredFontSize() {
    if (hasCaption()) {
      FontMetrics fm = getFontMetrics(getFont());
      int preferredHeight = fm.getHeight();
      int preferredWidth = getPreferredWidth(fm);

      return new Dimension(preferredWidth, preferredHeight + getVgap() * 2);
    }

    return new Dimension(0, getVgap() * 2 + 1);
  }

  private int getPreferredWidth(FontMetrics fm) {
    return fm.stringWidth(myCaption) + 2 * getHgap();
  }

  private boolean hasCaption() {
    return myCaption != null && !"".equals(myCaption.trim());
  }

  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  public void setMinimumWidth(int width) {
    myPrefWidth = width;
  }

  protected void paintComponent(Graphics g) {
    g.setColor(GroupedElementsRenderer.POPUP_SEPARATOR_FOREGROUND);

    if (hasCaption()) {
      final FontMetrics fm = getFontMetrics(getFont());
      final int baseline = getVgap() + fm.getAscent();

      final int fontWidth = getPreferredFontSize().width;
      final int lineX = (getWidth() - fontWidth) / 2;
      final int lineY = getVgap() + fm.getHeight() / 2;

      g.drawLine(0, lineY, lineX, lineY);
      g.drawLine(lineX + fontWidth, lineY, getWidth() - 1, lineY);

      UIUtil.applyRenderingHints(g);
      g.setColor(GroupedElementsRenderer.POPUP_SEPARATOR_TEXT_FOREGROUND);
      g.drawString(myCaption, lineX + getHgap(), baseline);
    }
    else {
      g.drawLine(0, getVgap(), getWidth() - 1, getVgap());
    }
  }

  public void setCaption(String captionAboveOf) {
    myCaption = captionAboveOf;
  }
}