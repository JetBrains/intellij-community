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
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;

import static com.intellij.ui.paint.RectanglePainter.FILL;
import static javax.swing.SwingConstants.CENTER;
import static javax.swing.SwingConstants.LEFT;
import static javax.swing.SwingUtilities.layoutCompoundLabel;

public class SeparatorWithText extends JComponent implements Accessible {

  private String myCaption;
  private int myPrefWidth;
  private int myAlignment;
  private Color myTextForeground;

  public SeparatorWithText() {
    setBorder(BorderFactory.createEmptyBorder(getVgap(), 0, getVgap(), 0));
    setFont(UIUtil.getLabelFont());
    setFont(getFont().deriveFont(Font.BOLD));
    setForeground(GroupedElementsRenderer.POPUP_SEPARATOR_FOREGROUND);
    setTextForeground(GroupedElementsRenderer.POPUP_SEPARATOR_TEXT_FOREGROUND);
  }

  public Color getTextForeground() {
    return myTextForeground;
  }

  public void setTextForeground(@NotNull Color textForeground) {
    myTextForeground = textForeground;
  }

  protected static int getVgap() {
    return UIUtil.isUnderNativeMacLookAndFeel() ? 1 : 3;
  }

  protected static int getHgap() {
    return 3;
  }

  public void setCaptionCentered(boolean captionCentered) {
    myAlignment = captionCentered ? CENTER : LEFT;
  }

  public Dimension getPreferredSize() {
    return isPreferredSizeSet() ? super.getPreferredSize() : getPreferredFontSize();
  }

  private Dimension getPreferredFontSize() {
    Dimension size = new Dimension(myPrefWidth < 0 ? 0 : myPrefWidth, 1);
    String caption = getCaption();
    if (caption != null) {
      FontMetrics fm = getFontMetrics(getFont());
      size.height = fm.getHeight();
      if (myPrefWidth < 0) {
        size.width = 2 * getHgap() + fm.stringWidth(caption);
      }
    }
    JBInsets.addTo(size, getInsets());
    return size;
  }

  public Dimension getMinimumSize() {
    return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredFontSize();
  }

  public void setMinimumWidth(int width) {
    myPrefWidth = width;
  }

  protected void paintComponent(Graphics g) {
    g.setColor(getForeground());

    Rectangle bounds = new Rectangle(getWidth(), getHeight());
    JBInsets.removeFrom(bounds, getInsets());

    String caption = getCaption();
    if (caption != null) {
      int hGap = getHgap();
      bounds.x += hGap;
      bounds.width -= hGap + hGap;

      Rectangle iconR = new Rectangle();
      Rectangle textR = new Rectangle();
      FontMetrics fm = g.getFontMetrics();
      String label = layoutCompoundLabel(fm, caption, null, CENTER, myAlignment, CENTER, myAlignment, bounds, iconR, textR, 0);
      textR.y += fm.getAscent();
      if (caption.equals(label)) {
        int y = textR.y + (int)fm.getLineMetrics(label, g).getStrikethroughOffset();
        paintLinePart(g, bounds.x, textR.x, -hGap, y);
        paintLinePart(g, textR.x + textR.width, bounds.x + bounds.width, hGap, y);
      }
      UISettings.setupAntialiasing(g);
      g.setColor(getTextForeground());
      g.drawString(label, textR.x, textR.y);
    }
    else {
      paintLine(g, bounds.x, bounds.y, bounds.width);
    }
  }

  protected void paintLinePart(Graphics g, int xMin, int xMax, int hGap, int y) {
    if (xMax > xMin) paintLine(g, xMin + hGap, y, xMax - xMin);
  }

  protected void paintLine(Graphics g, int x, int y, int width) {
    FILL.paint((Graphics2D)g, x, y, width, 1, null);
  }

  protected String getCaption() {
    return myCaption == null || myCaption.trim().isEmpty() ? null : myCaption;
  }

  public void setCaption(String captionAboveOf) {
    myCaption = captionAboveOf;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleSeparatorWithText();
    }
    return accessibleContext;
  }

  protected class AccessibleSeparatorWithText extends AccessibleJComponent {
    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }

    @Override
    public String getAccessibleName() {
      return myCaption;
    }
  }
}