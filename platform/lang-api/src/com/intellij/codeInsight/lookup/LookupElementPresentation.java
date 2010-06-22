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
  private boolean myItemTextUnderlined;

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
   * Is equivalent to instanceof {@link com.intellij.codeInsight.lookup.RealLookupElementPresentation} check.
   *
   * @return whether the presentation is requested to actually render lookup element on screen, or just to estimate its width.
   * In the second, 'non-real' case, some heavy operations (e.g. getIcon()) can be omitted (only icon width is important)
   */
  public boolean isReal() {
    return false;
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

  public boolean isItemTextUnderlined() {
    return myItemTextUnderlined;
  }

  public void setItemTextUnderlined(boolean itemTextUnderlined) {
    myItemTextUnderlined = itemTextUnderlined;
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
