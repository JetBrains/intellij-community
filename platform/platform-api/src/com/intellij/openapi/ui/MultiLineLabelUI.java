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
package com.intellij.openapi.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ui.UIUtil;

import javax.swing.*;
import javax.swing.plaf.basic.BasicGraphicsUtils;
import javax.swing.plaf.basic.BasicLabelUI;
import java.awt.*;
import java.util.ArrayList;
import java.util.StringTokenizer;

/**
 * Based on Zafir Anjum example.
 */
public class MultiLineLabelUI extends BasicLabelUI {
  private String myString;
  private String[] myLines;

  protected String layoutCL(
    JLabel label,
    FontMetrics fontMetrics,
    String text,
    Icon icon,
    Rectangle viewR,
    Rectangle iconR,
    Rectangle textR) {
    String s = layoutCompoundLabel(
      label,
      fontMetrics,
      splitStringByLines(text),
      icon,
      label.getVerticalAlignment(),
      label.getHorizontalAlignment(),
      label.getVerticalTextPosition(),
      label.getHorizontalTextPosition(),
      viewR,
      iconR,
      textR,
      label.getIconTextGap());

    if ("".equals(s))
      return text;
    return s;
  }

  static final int LEADING = SwingConstants.LEADING;
  static final int TRAILING = SwingConstants.TRAILING;
  static final int LEFT = SwingConstants.LEFT;
  static final int RIGHT = SwingConstants.RIGHT;
  static final int TOP = SwingConstants.TOP;
  static final int CENTER = SwingConstants.CENTER;

  /**
   * Compute and return the location of the icons origin, the
   * location of origin of the text baseline, and a possibly clipped
   * version of the compound labels string.  Locations are computed
   * relative to the viewR rectangle.
   * The JComponents orientation (LEADING/TRAILING) will also be taken
   * into account and translated into LEFT/RIGHT values accordingly.
   */
  public static String layoutCompoundLabel(JComponent c,
    FontMetrics fm,
    String[] text,
    Icon icon,
    int verticalAlignment,
    int horizontalAlignment,
    int verticalTextPosition,
    int horizontalTextPosition,
    Rectangle viewR,
    Rectangle iconR,
    Rectangle textR,
    int textIconGap) {
    boolean orientationIsLeftToRight = true;
    int hAlign = horizontalAlignment;
    int hTextPos = horizontalTextPosition;


    if (c != null) {
      if (!(c.getComponentOrientation().isLeftToRight())) {
        orientationIsLeftToRight = false;
      }
    }


    // Translate LEADING/TRAILING values in horizontalAlignment
    // to LEFT/RIGHT values depending on the components orientation
    switch (horizontalAlignment) {
    case LEADING:
      hAlign = (orientationIsLeftToRight) ? LEFT : RIGHT;
      break;
    case TRAILING:
      hAlign = (orientationIsLeftToRight) ? RIGHT : LEFT;
      break;
    }

    // Translate LEADING/TRAILING values in horizontalTextPosition
    // to LEFT/RIGHT values depending on the components orientation
    switch (horizontalTextPosition) {
    case LEADING:
      hTextPos = (orientationIsLeftToRight) ? LEFT : RIGHT;
      break;
    case TRAILING:
      hTextPos = (orientationIsLeftToRight) ? RIGHT : LEFT;
      break;
    }

    return layoutCompoundLabel(fm,
      text,
      icon,
      verticalAlignment,
      hAlign,
      verticalTextPosition,
      hTextPos,
      viewR,
      iconR,
      textR,
      textIconGap);
  }

  /**
   * Compute and return the location of the icons origin, the
   * location of origin of the text baseline, and a possibly clipped
   * version of the compound labels string.  Locations are computed
   * relative to the viewR rectangle.
   * This layoutCompoundLabel() does not know how to handle LEADING/TRAILING
   * values in horizontalTextPosition (they will default to RIGHT) and in
   * horizontalAlignment (they will default to CENTER).
   * Use the other version of layoutCompoundLabel() instead.
   */
  public static String layoutCompoundLabel(
    FontMetrics fm,
    String[] text,
    Icon icon,
    int verticalAlignment,
    int horizontalAlignment,
    int verticalTextPosition,
    int horizontalTextPosition,
    Rectangle viewR,
    Rectangle iconR,
    Rectangle textR,
    int textIconGap) {
    /* Initialize the icon bounds rectangle iconR.
     */

    if (icon != null) {
      iconR.width = icon.getIconWidth();
      iconR.height = icon.getIconHeight();
    }
    else {
      iconR.width = iconR.height = 0;
    }

    /* Initialize the text bounds rectangle textR.  If a null
     * or and empty String was specified we substitute "" here
     * and use 0,0,0,0 for textR.
     */

    // Fix for textIsEmpty sent by Paulo Santos
    boolean textIsEmpty =
      (text == null) || (text.length == 0) || (text.length == 1 && ((text[0] == null) || "".equals(text[0])));

    String rettext = "";
    if (textIsEmpty) {
      textR.width = textR.height = 0;
    }
    else {
      Dimension dim = computeMultiLineDimension(fm, text);
      textR.width = dim.width;
      textR.height = dim.height;
    }

    /* Unless both text and icon are non-null, we effectively ignore
     * the value of textIconGap.  The code that follows uses the
     * value of gap instead of textIconGap.
     */

    int gap = (textIsEmpty || (icon == null)) ? 0 : textIconGap;

    if (!textIsEmpty) {

      /* If the label text string is too wide to fit within the available
       * space "..." and as many characters as will fit will be
       * displayed instead.
       */

      int availTextWidth;

      if (horizontalTextPosition == CENTER) {
        availTextWidth = viewR.width;
      }
      else {
        availTextWidth = viewR.width - (iconR.width + gap);
      }


      if (textR.width > availTextWidth && text.length == 1) {
        String clipString = "...";
        int totalWidth = SwingUtilities.computeStringWidth(fm, clipString);
        int nChars;
        for (nChars = 0; nChars < text[0].length(); nChars++) {
          totalWidth += fm.charWidth(text[0].charAt(nChars));
          if (totalWidth > availTextWidth) {
            break;
          }
        }
        rettext = text[0].substring(0, nChars) + clipString;
        textR.width = SwingUtilities.computeStringWidth(fm, rettext);
      }
    }


    /* Compute textR.x,y given the verticalTextPosition and
     * horizontalTextPosition properties
     */

    if (verticalTextPosition == TOP) {
      if (horizontalTextPosition != CENTER) {
        textR.y = 0;
      }
      else {
        textR.y = -(textR.height + gap);
      }
    }
    else if (verticalTextPosition == CENTER) {
      textR.y = (iconR.height / 2) - (textR.height / 2);
    }
    else {
      // (verticalTextPosition == BOTTOM)
      if (horizontalTextPosition != CENTER) {
        textR.y = iconR.height - textR.height;
      }
      else {
        textR.y = (iconR.height + gap);
      }
    }

    if (horizontalTextPosition == LEFT) {
      textR.x = -(textR.width + gap);
    }
    else if (horizontalTextPosition == CENTER) {
      textR.x = (iconR.width / 2) - (textR.width / 2);
    }
    else {
      // (horizontalTextPosition == RIGHT)
      textR.x = (iconR.width + gap);
    }

    /* labelR is the rectangle that contains iconR and textR.
     * Move it to its proper position given the labelAlignment
     * properties.
     *
     * To avoid actually allocating a Rectangle, Rectangle.union
     * has been inlined below.
     */
    int labelR_x = Math.min(iconR.x, textR.x);
    int labelR_width = Math.max(iconR.x + iconR.width, textR.x + textR.width) - labelR_x;
    int labelR_y = Math.min(iconR.y, textR.y);
    int labelR_height = Math.max(iconR.y + iconR.height, textR.y + textR.height) - labelR_y;

    int dx, dy;

    if (verticalAlignment == TOP) {
      dy = viewR.y - labelR_y;
    }
    else if (verticalAlignment == CENTER) {
      dy = (viewR.y + (viewR.height / 2)) - (labelR_y + (labelR_height / 2));
    }
    else {
      // (verticalAlignment == BOTTOM)
      dy = (viewR.y + viewR.height) - (labelR_y + labelR_height);
    }

    if (horizontalAlignment == LEFT) {
      dx = viewR.x - labelR_x;
    }
    else if (horizontalAlignment == RIGHT) {
      dx = (viewR.x + viewR.width) - (labelR_x + labelR_width);
    }
    else {
      // (horizontalAlignment == CENTER)
      dx = (viewR.x + (viewR.width / 2)) -
        (labelR_x + (labelR_width / 2));
    }

    /* Translate textR and glypyR by dx,dy.
     */

    textR.x += dx;
    textR.y += dy;

    iconR.x += dx;
    iconR.y += dy;

    return rettext;
  }

  protected void paintEnabledText(JLabel l, Graphics g, String s, int textX, int textY) {
    int accChar = l.getDisplayedMnemonic();
    g.setColor(l.getForeground());
    drawString(g, s, accChar, textX, textY);
  }

  protected void paintDisabledText(JLabel l, Graphics g, String s, int textX, int textY) {
    int accChar = l.getDisplayedMnemonic();
    g.setColor(l.getBackground());
    drawString(g, s, accChar, textX, textY);
  }

  protected void drawString(Graphics g, String s, int accChar, int textX, int textY) {
    UISettings.setupAntialiasing(g);
    if (s.indexOf('\n') == -1)
      BasicGraphicsUtils.drawString(g, s, accChar, textX, textY);
    else {
      String[] strs = splitStringByLines(s);
      int height = g.getFontMetrics().getHeight();
      // Only the first line can have the accel char
      BasicGraphicsUtils.drawString(g, strs[0], accChar, textX, textY);
      for (int i = 1; i < strs.length; i++) {
        g.drawString(strs[i], textX, textY + (height * i));
      }
    }
  }

  public static Dimension computeMultiLineDimension(FontMetrics fm, String[] strs) {
    int width = 0;
    for (int i = 0; i < strs.length; i++) {
      width = Math.max(width, SwingUtilities.computeStringWidth(fm, strs[i]));
    }
    return new Dimension(width, fm.getHeight() * strs.length);
  }

  public String[] splitStringByLines(String str) {
    if (str == null) return ArrayUtil.EMPTY_STRING_ARRAY;
    if (str.equals(myString)) {
      return myLines;
    }
    myString = convertTabs(str, 2);

    ArrayList list = new ArrayList();
    StringTokenizer st = new StringTokenizer(str, "\n\r");
    while (st.hasMoreTokens()) {
      list.add(st.nextToken());
    }

    myLines = (String[])ArrayUtil.toStringArray(list);
    return myLines;
  }

  public static String convertTabs(String text, final int tabLength) {
    StringBuffer buf = new StringBuffer(text.length());
    for (int idx = 0; idx < text.length(); idx++) {
      char ch = text.charAt(idx);
      if (ch == '\t') {
        for (int i = 0; i < tabLength; i++) buf.append(' ');
      }
      else {
        buf.append(ch);
      }
    }
    return buf.toString();
  }
}
