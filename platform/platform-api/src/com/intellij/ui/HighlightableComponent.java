// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.ide.ui.UISettings;
import com.intellij.openapi.editor.markup.EffectType;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.dsl.builder.DslComponentProperty;
import com.intellij.ui.dsl.gridLayout.UnscaledGapsKt;
import com.intellij.ui.paint.LinePainter2D;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nls;
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

public class HighlightableComponent extends JComponent implements Accessible {
  protected @Nls String myText = "";
  protected Icon myIcon;
  protected int myIconTextGap;
  protected ArrayList<HighlightedRegion> myHighlightedRegions;
  protected boolean myIsSelected;
  protected boolean myHasFocus;
  protected boolean myPaintUnfocusedSelection = false;
  private boolean myDoNotHighlight = false;
  private boolean myIconAtRight = false;

  public HighlightableComponent() {
    myIconTextGap = 4;
    setText("");
    setOpaque(true);
    updateUI();
    setAlignmentX(LEFT_ALIGNMENT);
    updateDslVisualPaddings();
  }

  @Override public void updateUI() {
    UISettings.setupComponentAntialiasing(this);
    UISettings.setupFractionalMetrics(this);
  }

  public void setText(@Nullable @Nls String text) {
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

  public @NotNull String getText() {
    return myText;
  }

  public @Nullable Icon getIcon() {
    return myIcon;
  }

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
    updateDslVisualPaddings();
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

    if (myHighlightedRegions.isEmpty()){
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
        if (startOffset < hRegion.startOffset) {

          if (endOffset < hRegion.endOffset){
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
            hRegion.startOffset = endOffset;
          }
          else if (endOffset == hRegion.endOffset){
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));
          }
          else { // endOffset > hRegion.endOffset
            myHighlightedRegions.remove(hRegion);
            myHighlightedRegions.add(i, new HighlightedRegion(startOffset, hRegion.startOffset, attributes));
            myHighlightedRegions.add(i + 1, new HighlightedRegion(hRegion.startOffset, hRegion.endOffset, TextAttributes.merge(hRegion.textAttributes, attributes)));

            if (i < myHighlightedRegions.size() - 1){
              addHighlighter(i + 1, hRegion.endOffset, endOffset, attributes);
            }
            else{
              myHighlightedRegions.add(i + 2, new HighlightedRegion(hRegion.endOffset, endOffset, attributes));
            }
          }
          break;
        }

        // must be after and overlap or full overlap
        if (startOffset < hRegion.endOffset){

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

  @Override
  protected void paintComponent(Graphics g) {

    // determine color of background

    Color bgColor;
    Color fgColor;
    boolean paintHighlightsBackground;
    boolean paintHighlightsForeground;
    if (myIsSelected && (myHasFocus || myPaintUnfocusedSelection)) {
      bgColor = UIUtil.getTreeSelectionBackground(true);
      fgColor = UIUtil.getTreeSelectionForeground(true);
      paintHighlightsBackground = false;
      paintHighlightsForeground = false;
    }
    else {
      bgColor = myEnforcedBackground == null ? UIUtil.getTreeBackground() : myEnforcedBackground;
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
      g.fillRect(0, 0, Math.max(0, textOffset - 2), getHeight());
      g.setColor(bgColor);
      g.fillRect(Math.max(0, textOffset - 2), 0, getWidth(), getHeight());
    }

    // paint icon

    if (myIcon != null) {
      int x = getIconOffset();
      myIcon.paintIcon(this, g, x, (getHeight() - myIcon.getIconHeight()) / 2);
    }

    // paint text

    applyRenderingHints(g);
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    if (myText == null) {
      myText = "";
    }
    // center text inside the component:
    final int yOffset = (getHeight() - defFontMetrics.getMaxAscent() - defFontMetrics.getMaxDescent()) / 2 + defFontMetrics.getMaxAscent();
    if (myHighlightedRegions.isEmpty()){
      g.setColor(fgColor);
      g.drawString(myText, textOffset, yOffset/*defFontMetrics.getMaxAscent()*/);
    }
    else{
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {

        String text = myText.substring(endIndex, hRegion.startOffset);
        endIndex = hRegion.endOffset;

        // draw plain text

        if (!text.isEmpty()) {
          g.setColor(fgColor);
          g.setFont(defFontMetrics.getFont());

          g.drawString(text, offset, yOffset/*defFontMetrics.getMaxAscent()*/);

          offset += getStringWidth(text, defFontMetrics);
        }

        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);

        text = myText.substring(hRegion.startOffset, hRegion.endOffset);

        // paint highlight background

        if (hRegion.textAttributes.getBackgroundColor() != null && paintHighlightsBackground) {
          g.setColor(hRegion.textAttributes.getBackgroundColor());
          g.fillRect(offset, 0, getStringWidth(text, fontMetrics), fontMetrics.getHeight() + fontMetrics.getLeading());
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
          LinePainter2D.paint((Graphics2D)g, offset, y, offset + getStringWidth(text, fontMetrics) - 1, y);
        }

        // draw highlight border

        if (hRegion.textAttributes.getEffectColor() != null && hRegion.textAttributes.getEffectType() == EffectType.BOXED) {
          g.setColor(hRegion.textAttributes.getEffectColor());
          g.drawRect(offset, 0, getStringWidth(text, fontMetrics) - 1, fontMetrics.getHeight() + fontMetrics.getLeading() - 1);
        }

        offset += getStringWidth(text, fontMetrics);
      }

      String text = myText.substring(endIndex);

      if (!text.isEmpty()){
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

  protected int getTextOffset() {
    if (myIcon == null || isIconAtRight()) {
      return 2;
    }
    return myIcon.getIconWidth() + myIconTextGap;
  }

  protected int getIconOffset() {
    return isIconAtRight() ? getWidth() - myIcon.getIconWidth() : 0;
  }

  public @Nullable HighlightedRegion findRegionByX(int x) {
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int width = getTextOffset();
    if (width > x) return null;

    if (!myText.isEmpty() && !myHighlightedRegions.isEmpty()) {
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {
        width += getStringWidth(myText.substring(endIndex, hRegion.startOffset), defFontMetrics);
        endIndex = hRegion.endOffset;
        if (width > x) return null;

        String text = getRegionText(hRegion);
        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);

        width += getStringWidth(text, fontMetrics);
        if (width > x) return hRegion;
      }
    }
    return null;
  }

  public @NotNull Map<String, Rectangle> getHighlightedRegionsBoundsMap() {

    HashMap<String, Rectangle> map = new HashMap<>();
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int pivot = getTextOffset();
    int start, end;

    if (!myText.isEmpty() && !myHighlightedRegions.isEmpty()) {
      int endIndex = 0;
      for (HighlightedRegion hRegion : myHighlightedRegions) {
        pivot += getStringWidth(myText.substring(endIndex, hRegion.startOffset), defFontMetrics);
        start = pivot;
        endIndex = hRegion.endOffset;

        String text = getRegionText(hRegion);
        Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
        FontMetrics fontMetrics = getFontMetrics(regFont);
        pivot += getStringWidth(text, fontMetrics);
        end = pivot;
        map.put(text, new Rectangle(this.getBounds().x + start, this.getBounds().y, end - start, this.getBounds().height));
      }
    }
    return map;
  }

  @Override
  public Dimension getPreferredSize() {
    FontMetrics defFontMetrics = getFontMetrics(getFont());

    int width = getTextOffset();

    if (!myText.isEmpty()){
      if (myHighlightedRegions.isEmpty()){
        width += getStringWidth(myText, defFontMetrics);
      }
      else{
        int endIndex = 0;
        for (HighlightedRegion hRegion : myHighlightedRegions) {
          width += getStringWidth(myText.substring(endIndex, hRegion.startOffset), defFontMetrics);
          endIndex = hRegion.endOffset;

          String text = getRegionText(hRegion);
          Font regFont = getFont().deriveFont(hRegion.textAttributes.getFontType());
          FontMetrics fontMetrics = getFontMetrics(regFont);

          width += getStringWidth(text, fontMetrics);
        }
        width += getStringWidth(myText.substring(endIndex), defFontMetrics);
      }
    }

    int height = defFontMetrics.getHeight() + defFontMetrics.getLeading();

    if (myIcon != null){
      height = Math.max(myIcon.getIconHeight() + defFontMetrics.getLeading(), height);
      width += myIcon.getIconWidth();
    }

    return new Dimension(width + 2, height);
  }

  protected int getStringWidth(@Nls String text, FontMetrics fontMetrics) {
    return fontMetrics.stringWidth(text);
  }

  @ApiStatus.Internal
  public ArrayList<HighlightedRegion> getHighlightedRegions() {
    return myHighlightedRegions;
  }

  public @Nls String getRegionText(HighlightedRegion hRegion) {
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

  public boolean isIconAtRight() {
    return myIconAtRight;
  }

  public void setIconAtRight(boolean iconAtRight) {
    myIconAtRight = iconAtRight;
    updateDslVisualPaddings();
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

  private void updateDslVisualPaddings() {
    int left = myIcon == null || isIconAtRight() ? getTextOffset() : 0;
    putClientProperty(DslComponentProperty.VISUAL_PADDINGS, UnscaledGapsKt.UnscaledGaps(0, left, 0 ,0));
  }
}
