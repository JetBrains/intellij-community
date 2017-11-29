/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.accessibility.Accessible;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Eugene Belyaev
 */
public class HighlightableComponent extends JComponent implements Accessible {
  protected String myText = "";
  protected Icon myIcon;
  protected int myIconTextGap;
  protected ArrayList<HighlightedRegion> myHighlightedRegions;
  protected boolean myIsSelected;
  protected boolean myHasFocus;
  protected boolean myPaintUnfocusedSelection = false;
  private boolean myDoNotHighlight = false;

  public HighlightableComponent() {
    myIconTextGap = 4;
    setText("");
    setOpaque(true);
    updateUI();
  }

  @Override public void updateUI() {
    UISettings.setupComponentAntialiasing(this);
  }

  public void setText(String text) {
    String oldAccessibleName = null;
    if (accessibleContext != null) {
      oldAccessibleName = accessibleContext.getAccessibleName();
    }

    if (text == null) {
      text = "";
    }
    myText = text;
    myHighlightedRegions = new ArrayList<>(4);

    if ((accessibleContext != null) && !StringUtil.equals(accessibleContext.getAccessibleName(), oldAccessibleName)) {
      accessibleContext.firePropertyChange(
        AccessibleContext.ACCESSIBLE_VISIBLE_DATA_PROPERTY,
        oldAccessibleName,
        accessibleContext.getAccessibleName());
    }
  }

  public void setIcon(Icon icon) {
    myIcon = icon;
    invalidate();
    repaint();
  }

  public void addHighlighter(int startOffset, int endOffset, TextAttributes attributes) {
    addHighlighter(0, startOffset, endOffset, attributes);
  }

  private void addHighlighter(int startIndex, int startOffset, int endOffset, TextAttributes attributes) {
    if (startOffset < 0) startOffset = 0;
    if (endOffset > myText.length()) endOffset = myText.length();

    if (startOffset >= endOffset) return;

    if (myHighlightedRegions.size() == 0){
      myHighlightedRegions.add(new HighlightedRegion(startOffset, endOffset, attributes));
    }
    else{
      for(int i = startIndex; i < myHighlightedRegions.size(); i++){
        HighlightedRegion hRegion = myHighlightedRegions.get(i);

        // must be before
        if (startOffset < hRegion.startOffset && endOffset <= hRegion.startOffset){
          myHighlightedRegions.add(i, new HighlightedRegion(startOffset, endOffset, attributes));
          break;
        }

        // must be after
        if (startOffset >= hRegion.endOffset){
          if (i == myHighlightedRegions.size() - 1){
            myHighlightedRegions.add(new HighlightedRegion(startOffset, endOffset, attributes));
            break;
          }
        }

        // must be before and overlap
        if (startOffset < hRegion.startOffset && endOffset > hRegion.startOffset){

          if (endOffset < hRegion.endOffset){
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            hRegion.startOffset = endOffset;
            break;
          }

          if (endOffset == hRegion.endOffset){
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            break;
          }

          if (endOffset > hRegion.endOffset){
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, hRegion.endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));

            if (i < myHighlightedRegions.size() - 1){
              addHighlighter(i + 1, hRegion.endOffset, endOffset, attributes);
            }
            else{
              myHighlightedRegions.add(i + 2, new HighlightedRegion(hRegion.endOffset, endOffset, attributes));
            }
            break;
          }
        }

        // must be after and overlap or full overlap
        if (startOffset >= hRegion.startOffset && startOffset < hRegion.endOffset){

          int oldEndOffset = hRegion.endOffset;

          hRegion.endOffset = startOffset;

          if (endOffset < oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            myHighlightedRegions.add(i + 2, new HighlightedRegion(endOffset, oldEndOffset, hRegion.textAttributes));

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }

          if (endOffset == oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, oldEndOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }

          if (endOffset > oldEndOffset){
            myHighlightedRegions.add(i + 1, new HighlightedRegion(startOffset, oldEndOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            if (i < myHighlightedRegions.size() - 1){
              addHighlighter(i + 1, oldEndOffset, endOffset, attributes);
            }
            else{
              myHighlightedRegions.add(i + 2, new HighlightedRegion(hRegion.endOffset, endOffset, attributes));
            }

            if (startOffset == hRegion.startOffset){
              myHighlightedRegions.remove(hRegion);
            }

            break;
          }
        }
      }
    }
  }

  public void setIconTextGap(int gap) {
    myIconTextGap = Math.max(gap, 2);
  }

  public int getIconTextGap() {
    return myIconTextGap;
  }

  private Color myEnforcedBackground = null;
  protected void enforceBackgroundOutsideText(Color bg) {
    myEnforcedBackground = bg;
  }

  protected void setDoNotHighlight(final boolean b) {
    myDoNotHighlight = b;
  }

  protected void paintComponent(Graphics g) {

    // determine color of background

    Color bgColor;
    Color fgColor;
    boolean paintHighlightsBackground;
    boolean paintHighlightsForeground;
    if (myIsSelected && (myHasFocus || myPaintUnfocusedSelection)) {
      bgColor = UIUtil.getTreeSelectionBackground();
      fgColor = UIUtil.getTreeSelectionForeground();
      paintHighlightsBackground = false;
      paintHighlightsForeground = false;
    }
    else {
      bgColor = myEnforcedBackground == null ? UIUtil.getTreeTextBackground() : myEnforcedBackground;
      fgColor = getForeground();
      paintHighlightsBackground = isOpaque();
      paintHighlightsForeground = true;
    }

    if (myDoNotHighlight) {
      paintHighlightsForeground = false;
    }

    // paint background

    int textOffset = getTextOffset();
    int offset = textOffset;

    if (isOpaque()) {
      g.setColor(getBackground());
      g.fillRect(0,0,textOffset-2,getHeight());
      g.setColor(bgColor);
      g.fillRect(textOffset-2, 0, getWidth(), getHeight());
    }

    // paint icon

    if (myIcon != null) {
      myIcon.paintIcon(this, g, 0, (getHeight() - myIcon.getIconHeight()) / 2);
    }

    // paint text

    applyRenderingHints(g);
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    if (myText == null) {
      myText = "";
    }
    // center text inside the component:
    final int yOffset = (getHeight() - defFontMetrics.getMaxAscent() - defFontMetrics.getMaxDescent()) / 2 + defFontMetrics.getMaxAscent() - 1;
    if (myHighlightedRegions.size() == 0){
      g.setColor(fgColor);
      g.drawString(myText, textOffset, yOffset/*defFontMetrics.getMaxAscent()*/);
    }
    else{
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {

        String text = myText.substring(endIndex, hRegion.startOffset);
        endIndex = hRegion.endOffset;

        // draw plain text

        if (text.length() != 0) {
          g.setColor(fgColor);
          g.setFont(defFontMetrics.getFont());

          g.drawString(text, offset, yOffset/*defFontMetrics.getMaxAscent()*/);

          offset += defFontMetrics.stringWidth(text);
        }

        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);

        text = myText.substring(hRegion.startOffset, hRegion.endOffset);

        // paint highlight background

        if (hRegion.textAttributes.getBackgroundColor() != null && paintHighlightsBackground) {
          g.setColor(hRegion.textAttributes.getBackgroundColor());
          g.fillRect(offset, 0, fontMetrics.stringWidth(text), fontMetrics.getHeight() + fontMetrics.getLeading());
        }

        // draw highlight text

        if (hRegion.textAttributes.getForegroundColor() != null && paintHighlightsForeground) {
          g.setColor(hRegion.textAttributes.getForegroundColor());
        } else {
          g.setColor(fgColor);
        }

        g.setFont(fontMetrics.getFont());
        g.drawString(text, offset, yOffset/*fontMetrics.getMaxAscent()*/);

        // draw highlight underscored line

        if (hRegion.textAttributes.getEffectType() != null && hRegion.textAttributes.getEffectColor() != null) {
          g.setColor(hRegion.textAttributes.getEffectColor());
          int y = yOffset/*fontMetrics.getMaxAscent()*/ + 2;
          UIUtil.drawLine(g, offset, y, offset + fontMetrics.stringWidth(text) - 1, y);
        }

        // draw highlight border

        if (hRegion.textAttributes.getEffectColor() != null && hRegion.textAttributes.getEffectType() == EffectType.BOXED) {
          g.setColor(hRegion.textAttributes.getEffectColor());
          g.drawRect(offset, 0, fontMetrics.stringWidth(text) - 1, fontMetrics.getHeight() + fontMetrics.getLeading() - 1);
        }

        offset += fontMetrics.stringWidth(text);
      }

      String text = myText.substring(endIndex, myText.length());

      if (text.length() != 0){
        g.setColor(fgColor);
        g.setFont(defFontMetrics.getFont());

        g.drawString(text, offset, yOffset/*defFontMetrics.getMaxAscent()*/);
      }
    }

    // paint border

    if (myIsSelected){
      g.setColor(UIUtil.getTreeSelectionBorderColor());
      UIUtil.drawDottedRectangle(g, textOffset - 2, 0, getWidth() - 1, getHeight() - 1);
    }

    super.paintComponent(g);
  }

  protected void applyRenderingHints(Graphics g) {
    UISettings.setupAntialiasing(g);
  }

  private int getTextOffset() {
    if (myIcon == null){
      return 2;
    }
    return myIcon.getIconWidth() + myIconTextGap;
  }

  @Nullable
  public HighlightedRegion findRegionByX(int x) {
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int width = getTextOffset();
    if (width > x) return null;

    if (myText.length() != 0 && myHighlightedRegions.size() != 0) {
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {
        width += defFontMetrics.stringWidth(myText.substring(endIndex, hRegion.startOffset));
        endIndex = hRegion.endOffset;
        if (width > x) return null;

        String text = getRegionText(hRegion);
        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);

        width += fontMetrics.stringWidth(text);
        if (width > x) return hRegion;
      }
    }
    return null;
  }

  @NotNull
  public Map<String, Rectangle> getHightlightedRegionsBoundsMap() {

    HashMap<String, Rectangle> map = new HashMap<>();
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int pivot = getTextOffset();
    int start, end;

    if (myText.length() != 0 && myHighlightedRegions.size() != 0) {
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {
        pivot += defFontMetrics.stringWidth(myText.substring(endIndex, hRegion.startOffset));
        start = pivot;
        endIndex = hRegion.endOffset;

        String text = getRegionText(hRegion);
        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);
        pivot += fontMetrics.stringWidth(text);
        end = pivot;
        map.put(text, new Rectangle(this.getBounds().x + start, this.getBounds().y, end, this.getBounds().height));
      }
    }
    return map;
  }

  public Dimension getPreferredSize() {
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int width = getTextOffset();

    if (myText.length() != 0){
      if (myHighlightedRegions.size() == 0){
        width += defFontMetrics.stringWidth(myText);
      }
      else{
        int endIndex = 0;
        for (HighlightedRegion hRegion : myHighlightedRegions) {
          width += defFontMetrics.stringWidth(myText.substring(endIndex, hRegion.startOffset));
          endIndex = hRegion.endOffset;

          String text = getRegionText(hRegion);
          Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
          FontMetrics fontMetrics = getFontMetrics(regFont);

          width += fontMetrics.stringWidth(text);
        }
        width += defFontMetrics.stringWidth(myText.substring(endIndex, myText.length()));
      }
    }

    int height = defFontMetrics.getHeight() + defFontMetrics.getLeading();

    if (myIcon != null){
      height = Math.max(myIcon.getIconHeight() + defFontMetrics.getLeading(), height);
    }

    return new Dimension(width + 2, height);
  }

  public String getRegionText(HighlightedRegion hRegion) {
    String text;
    if (hRegion.endOffset > myText.length()) {
      if (hRegion.startOffset < myText.length()) {
        text = myText.substring(hRegion.startOffset);
      }
      else {
        text = "";
      }
    }
    else {
      text = myText.substring(hRegion.startOffset, hRegion.endOffset);
    }
    return text;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleHighlightable();
    }
    return accessibleContext;
  }

  protected class AccessibleHighlightable extends JComponent.AccessibleJComponent {
    @Override
    public String getAccessibleName() {
      return myText;
    }

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.LABEL;
    }
  }
}
