/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import java.awt.*;

import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;

public class SeparatorWithText extends JComponent {

  private String myCaption = "";
  private int myPrefWidth;
  private boolean myCaptionCentered = true;

  public SeparatorWithText() {
    setBorder(BorderFactory.createEmptyBorder(getVgap(), 0, getVgap(), 0));
    setFont(UIUtil.getLabelFont());
    setFont(getFont().deriveFont(Font.BOLD));
  }

  protected static int getVgap() {
    return UIUtil.isUnderNativeMacLookAndFeel() ? 1 : 3;
  }

  protected static int getHgap() {
    return 3;
  }

  public void setCaptionCentered(boolean captionCentered) {
    myCaptionCentered = captionCentered;
  }

  public Dimension getPreferredSize() {
    final Dimension size = getPreferredFontSize();
    size.width = myPrefWidth == -1 ? size.width : myPrefWidth;
    return size;
  }

  private Dimension getPreferredFontSize() {
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
    return myCaption != null && !myCaption.trim().isEmpty();
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
      Rectangle viewR = new Rectangle(0, getVgap(), getWidth() - 1, getHeight() - getVgap() - 1);
      Rectangle iconR = new Rectangle();
      Rectangle textR = new Rectangle();
      String s = SwingUtilities
        .layoutCompoundLabel(g.getFontMetrics(), myCaption, null, CENTER,
                             myCaptionCentered ? CENTER : LEFT,
                             CENTER,
                             myCaptionCentered ? CENTER : LEFT,
                             viewR, iconR, textR, 0);
      final int lineY = textR.y + textR.height / 2;
      if (s.equals(myCaption) && viewR.width - textR.width > 2 * getHgap()) {
        if (myCaptionCentered) {
          g.drawLine(0, lineY, textR.x - getHgap(), lineY);
        }
        g.drawLine(textR.x + textR.width + getHgap(), lineY, getWidth() - 1, lineY);
      }
      UISettings.setupAntialiasing(g);
      g.setColor(GroupedElementsRenderer.POPUP_SEPARATOR_TEXT_FOREGROUND);
      g.drawString(s, textR.x, textR.y + g.getFontMetrics().getAscent());
    }
    else {
      g.drawLine(0, getVgap(), getWidth() - 1, getVgap());
    }
  }

  protected String getCaption() {
    return myCaption;
  }

  public void setCaption(String captionAboveOf) {
    myCaption = captionAboveOf;
  }
}