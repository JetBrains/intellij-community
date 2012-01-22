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

import org.intellij.lang.annotations.JdkConstants;

import javax.swing.border.Border;
import javax.swing.border.TitledBorder;
import java.awt.*;

/**
 * @author Konstantin Bulenkov
 */
public class TitledBorderWithMnemonic extends TitledBorder {
  private final String myOriginalTitle;

  public TitledBorderWithMnemonic(String title) {
    this(null, title, LEADING, DEFAULT_POSITION, null, null);
  }

  public TitledBorderWithMnemonic(Border border) {
    this(border, "", LEADING, DEFAULT_POSITION, null, null);
  }

  public TitledBorderWithMnemonic(Border border, String title) {
    this(border, title, LEADING, DEFAULT_POSITION, null, null);
  }

  public TitledBorderWithMnemonic(Border border, String title, @JdkConstants.TitledBorderJustification int titleJustification, @JdkConstants.TitledBorderTitlePosition int titlePosition) {
    this(border, title, titleJustification, titlePosition, null, null);
  }

  public TitledBorderWithMnemonic(Border border, String title, @JdkConstants.TitledBorderJustification int titleJustification, @JdkConstants.TitledBorderTitlePosition int titlePosition, Font titleFont) {
    this(border, title, titleJustification, titlePosition, titleFont, null);
  }

  public TitledBorderWithMnemonic(Border border, String title, @JdkConstants.TitledBorderJustification int titleJustification, @JdkConstants.TitledBorderTitlePosition int titlePosition, Font titleFont,
                                  Color titleColor) {
    super(border, title.replaceAll("&", ""), titleJustification, titlePosition, titleFont, titleColor);
    myOriginalTitle = title;
  }

  public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
    Point textLoc = new Point();
    Border border = getBorder();

    if (getTitle() == null || getTitle().length() == 0) {
        if (border != null) {
            border.paintBorder(c, g, x, y, width, height);
        }
        return;
    }

    Rectangle grooveRect = new Rectangle(x + EDGE_SPACING, y + EDGE_SPACING,
                                         width - (EDGE_SPACING * 2),
                                         height - (EDGE_SPACING * 2));
    Font font = g.getFont();
    Color color = g.getColor();

    g.setFont(getFont(c));
    
    FontMetrics fm = g.getFontMetrics();
    int         fontHeight = fm.getHeight();
    int         descent = fm.getDescent();
    int         ascent = fm.getAscent();
    int         diff;
    int         stringWidth = fm.stringWidth(getTitle());
    Insets      insets;

    if (border != null) {
        insets = border.getBorderInsets(c);
    } else {
        insets = new Insets(0, 0, 0, 0);
    }

    int titlePos = getTitlePosition();
    switch (titlePos) {
        case ABOVE_TOP:
            diff = ascent + descent + (Math.max(EDGE_SPACING,
                             TEXT_SPACING*2) - EDGE_SPACING);
            grooveRect.y += diff;
            grooveRect.height -= diff;
            textLoc.y = grooveRect.y - (descent + TEXT_SPACING);
            break;
        case TOP:
        case DEFAULT_POSITION:
            diff = Math.max(0, ((ascent/2) + TEXT_SPACING) - EDGE_SPACING);
            grooveRect.y += diff;
            grooveRect.height -= diff;
            textLoc.y = (grooveRect.y - descent) +
            (insets.top + ascent + descent)/2;
            break;
        case BELOW_TOP:
            textLoc.y = grooveRect.y + insets.top + ascent + TEXT_SPACING;
            break;
        case ABOVE_BOTTOM:
            textLoc.y = (grooveRect.y + grooveRect.height) -
            (insets.bottom + descent + TEXT_SPACING);
            break;
        case BOTTOM:
            grooveRect.height -= fontHeight/2;
            textLoc.y = ((grooveRect.y + grooveRect.height) - descent) +
                    ((ascent + descent) - insets.bottom)/2;
            break;
        case BELOW_BOTTOM:
            grooveRect.height -= fontHeight;
            textLoc.y = grooveRect.y + grooveRect.height + ascent +
                    TEXT_SPACING;
            break;
    }

    int justification = getTitleJustification();
    if(c.getComponentOrientation().isLeftToRight()) {
        if(justification==LEADING ||
           justification==DEFAULT_JUSTIFICATION) {
            justification = LEFT;
        }
        else if(justification==TRAILING) {
            justification = RIGHT;
        }
    }
    else {
        if(justification==LEADING ||
           justification==DEFAULT_JUSTIFICATION) {
            justification = RIGHT;
        }
        else if(justification==TRAILING) {
            justification = LEFT;
        }
    }

    switch (justification) {
        case LEFT:
            textLoc.x = grooveRect.x + TEXT_INSET_H + insets.left;
            break;
        case RIGHT:
            textLoc.x = (grooveRect.x + grooveRect.width) -
                    (stringWidth + TEXT_INSET_H + insets.right);
            break;
        case CENTER:
            textLoc.x = grooveRect.x +
                    ((grooveRect.width - stringWidth) / 2);
            break;
    }

    // If title is positioned in middle of border AND its fontsize
    // is greater than the border's thickness, we'll need to paint
    // the border in sections to leave space for the component's background
    // to show through the title.
    //
    if (border != null) {
        if (((titlePos == TOP || titlePos == DEFAULT_POSITION) &&
              (grooveRect.y > textLoc.y - ascent)) ||
             (titlePos == BOTTOM &&
              (grooveRect.y + grooveRect.height < textLoc.y + descent))) {

            Rectangle clipRect = new Rectangle();

            // save original clip
            Rectangle saveClip = g.getClipBounds();

            // paint strip left of text
            clipRect.setBounds(saveClip);
            if (computeIntersection2(clipRect, x, y, textLoc.x-1-x, height)) {
                g.setClip(clipRect);
                border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                              grooveRect.width, grooveRect.height);
            }

            // paint strip right of text
            clipRect.setBounds(saveClip);
            if (computeIntersection2(clipRect, textLoc.x+stringWidth+1, y,
                           x+width-(textLoc.x+stringWidth+1), height)) {
                g.setClip(clipRect);
                border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                              grooveRect.width, grooveRect.height);
            }

            if (titlePos == TOP || titlePos == DEFAULT_POSITION) {
                // paint strip below text
                clipRect.setBounds(saveClip);
                if (computeIntersection2(clipRect, textLoc.x-1, textLoc.y+descent,
                                    stringWidth+2, y+height-textLoc.y-descent)) {
                    g.setClip(clipRect);
                    border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                              grooveRect.width, grooveRect.height);
                }

            } else { // titlePos == BOTTOM
              // paint strip above text
                clipRect.setBounds(saveClip);
                if (computeIntersection2(clipRect, textLoc.x-1, y,
                      stringWidth+2, textLoc.y - ascent - y)) {
                    g.setClip(clipRect);
                    border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                              grooveRect.width, grooveRect.height);
                }
            }

            // restore clip
            g.setClip(saveClip);

        } else {
            border.paintBorder(c, g, grooveRect.x, grooveRect.y,
                              grooveRect.width, grooveRect.height);
        }
    }

    g.setColor(getTitleColor());
    g.drawString(getTitle(), textLoc.x, textLoc.y);

    final int index = myOriginalTitle.indexOf('&');
    if (index != -1 && index != myOriginalTitle.length() - 1 && index == myOriginalTitle.lastIndexOf('&') && g instanceof Graphics2D) {
      int x0 = fm.stringWidth(getTitle().substring(0, index));
      int x1 = fm.stringWidth(getTitle().substring(0, index+1));
      ((Graphics2D)g).setPaint(getTitleColor());
      g.drawLine(textLoc.x + x0 - 1, textLoc.y + 1, textLoc.x + x1 - 1, textLoc.y + 1);
      ((Graphics2D)g).setPaint(color);
    }

    g.setFont(font);
    g.setColor(color);
  }

  private static boolean computeIntersection2(Rectangle dest,
                                             int rx, int ry, int rw, int rh) {
      int x1 = Math.max(rx, dest.x);
      int x2 = Math.min(rx + rw, dest.x + dest.width);
      int y1 = Math.max(ry, dest.y);
      int y2 = Math.min(ry + rh, dest.y + dest.height);
      dest.x = x1;
      dest.y = y1;
      dest.width = x2 - x1;
      dest.height = y2 - y1;

      if (dest.width <= 0 || dest.height <= 0) {
          return false;
      }
      return true;
  }
}
