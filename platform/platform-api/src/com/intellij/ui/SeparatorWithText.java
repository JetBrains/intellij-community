// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.JBInsets;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.StartupUiUtil;
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
  protected @NlsContexts.Separator String myCaption;
  protected int myPrefWidth;
  protected int myAlignment;
  protected Color myTextForeground;

  public SeparatorWithText() {
    setBorder(BorderFactory.createEmptyBorder(getVgap(), 0, getVgap(), 0));
    setFont(StartupUiUtil.getLabelFont());
    setFont(getFont().deriveFont(Font.BOLD));
    setForeground(JBUI.CurrentTheme.Popup.separatorColor());
    setTextForeground(JBUI.CurrentTheme.Popup.separatorTextColor());
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

  @Override
  public Dimension getPreferredSize() {
    return isPreferredSizeSet() ? super.getPreferredSize() : getPreferredElementSize();
  }

  protected Dimension getPreferredElementSize() {
    Dimension size = getLabelSize(new Insets(0, getHgap(), 0, getHgap()));
    JBInsets.addTo(size, getInsets());
    return size;
  }

  @NotNull
  protected Dimension getLabelSize(Insets labelInsets) {
    String caption = getCaption();
    if (caption == null) {
      return new Dimension(Math.max(myPrefWidth, 0), 1);
    }

    FontMetrics fm = getFontMetrics(getFont());
    int width = myPrefWidth < 0 ? fm.stringWidth(caption) + labelInsets.left + labelInsets.right : myPrefWidth;
    return new Dimension(width, fm.getHeight() + labelInsets.top + labelInsets.bottom);
  }

  @Override
  public Dimension getMinimumSize() {
    return isMinimumSizeSet() ? super.getMinimumSize() : getPreferredElementSize();
  }

  public void setMinimumWidth(int width) {
    myPrefWidth = width;
  }

  @Override
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

  public @NlsContexts.Separator String getCaption() {
    return myCaption == null || myCaption.trim().isEmpty() ? null : myCaption;
  }

  public void setCaption(@NlsContexts.Separator String captionAboveOf) {
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