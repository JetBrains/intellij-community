/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.lookup;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author peter
 */
public class LookupElementPresentation {
  private Icon myIcon;
  private Icon myTypeIcon;
  private String myItemText;
  private String myTailText;
  private String myTypeText;
  private boolean myStrikeout;
  private boolean myTailGrayed;
  private Color myTailForeground;
  private boolean myItemTextBold;

  private final boolean myReal;

  public LookupElementPresentation(boolean real) {
    myReal = real;
  }

  public void setIcon(@Nullable Icon icon) {
    myIcon = icon;
  }

  public void setItemText(@Nullable String text) {
    myItemText = text;
  }

  public void setStrikeout(boolean strikeout) {
    myStrikeout = strikeout;
  }

  public void setItemTextBold(boolean bold) {
    myItemTextBold = bold;
  }

  public void setTailText(@Nullable String text) {
    setTailText(text, false);
  }

  public void setTailText(@Nullable String text, boolean grayed) {
    myTailText = text;
    myTailGrayed = grayed;
  }

  public void setTailText(@Nullable String text, @Nullable Color foreground) {
    myTailText = text;
    myTailForeground = foreground;
  }

  public void setTypeText(@Nullable String text) {
    setTypeText(text, null);
  }

  public void setTypeText(@Nullable String text, @Nullable Icon icon) {
    myTypeText = text;
    myTypeIcon = icon;
  }

  /**
   * @return whether the presentation is requested to actually render lookup element on screen, or just to estimate its width.
   * In the second, 'non-real' case, some heavy operations (e.g. getIcon()) can be omitted (only icon width is important)
   */
  public boolean isReal() {
    return myReal;
  }

  @Nullable
  public Icon getIcon() {
    return myIcon;
  }

  @Nullable
  public Icon getTypeIcon() {
    return myTypeIcon;
  }

  @Nullable
  public String getItemText() {
    return myItemText;
  }

  @Nullable
  public String getTailText() {
    return myTailText;
  }

  @Nullable
  public String getTypeText() {
    return myTypeText;
  }

  public boolean isStrikeout() {
    return myStrikeout;
  }

  public boolean isTailGrayed() {
    return myTailGrayed;
  }

  @Nullable
  public Color getTailForeground() {
    return myTailForeground;
  }

  public boolean isItemTextBold() {
    return myItemTextBold;
  }

  public void copyFrom(@NotNull LookupElementPresentation presentation) {
    myIcon = presentation.myIcon;
    myTypeIcon = presentation.myTypeIcon;
    myItemText = presentation.myItemText;
    myTailText = presentation.myTailText;
    myTypeText = presentation.myTypeText;
    myStrikeout = presentation.myStrikeout;
    myTailGrayed = presentation.myTailGrayed;
    myTailForeground = presentation.myTailForeground;
    myItemTextBold = presentation.myItemTextBold;
  }
}
