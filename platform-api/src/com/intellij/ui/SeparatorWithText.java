package com.intellij.ui;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.awt.*;

public class SeparatorWithText extends JComponent {

  private static final int VGAP = 3;
  private static final int HGAP = 3;

  private String myCaption = "";
  private int myPrefWidth;

  public SeparatorWithText() {
    setBorder(BorderFactory.createEmptyBorder(VGAP, 0, VGAP, 0));
    @NonNls final String labelFont = "Label.font";
    setFont(UIManager.getFont(labelFont));
    setFont(getFont().deriveFont(Font.BOLD));
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

      return new Dimension(preferredWidth, preferredHeight + VGAP * 2);
    }

    return new Dimension(0, VGAP * 2 + 1);
  }

  private int getPreferredWidth(FontMetrics fm) {
    return fm.stringWidth(myCaption) + 2 * HGAP;
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
      FontMetrics fm = getFontMetrics(getFont());
      int baseline = VGAP + fm.getAscent();

      final int fontWidth = getPreferredFontSize().width;
      final int lineX = (getWidth() - fontWidth) / 2;
      final int lineY = VGAP + fm.getHeight() / 2;

      g.drawLine(0, lineY, lineX, lineY);
      g.drawString(myCaption, lineX + HGAP, baseline);
      g.drawLine(lineX + fontWidth, lineY, getWidth() - 1, lineY);
    }
    else {
      g.drawLine(0, VGAP, getWidth() - 1, VGAP);
    }
  }

  public void setCaption(String captionAboveOf) {
    myCaption = captionAboveOf;
  }

}