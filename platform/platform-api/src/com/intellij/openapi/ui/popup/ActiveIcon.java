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

package com.intellij.openapi.ui.popup;

import com.intellij.ui.IconReplacer;
import com.intellij.ui.icons.ReplaceableIcon;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class ActiveIcon implements Icon, ReplaceableIcon {

  private boolean myActive = true;

  private Icon myRegular;
  private Icon myInactive;

  public ActiveIcon(Icon icon) {
    this(icon, icon);
  }

  public ActiveIcon(@Nullable final Icon regular, @Nullable final Icon inactive) {
    setIcons(regular, inactive);
  }

  protected ActiveIcon(@NotNull ActiveIcon another) {
    this(another.myRegular, another.myInactive);
    myActive = another.myActive;
  }

  protected void setIcons(@Nullable final Icon regular, @Nullable final Icon inactive) {
    myRegular = regular != null ? regular : EmptyIcon.ICON_0;
    myInactive = inactive != null ? inactive : myRegular;
  }

  public Icon getRegular() {
    return myRegular;
  }

  public Icon getInactive() {
    return myInactive;
  }

  private Icon getIcon() {
    return myActive ? getRegular() : getInactive();
  }

  public void setActive(final boolean active) {
    myActive = active;
  }

  @Override
  public @NotNull ActiveIcon replaceBy(@NotNull IconReplacer replacer) {
    Icon regular = replacer.replaceIcon(myRegular);
    ActiveIcon icon = new ActiveIcon(regular, myRegular == myInactive ? regular : replacer.replaceIcon(myInactive));
    icon.myActive = myActive;
    return icon;
  }

  @Override
  public void paintIcon(final Component c, final Graphics g, final int x, final int y) {
    getIcon().paintIcon(c, g, x, y);
  }

  @Override
  public int getIconWidth() {
    return getIcon().getIconWidth();
  }

  @Override
  public int getIconHeight() {
    return getIcon().getIconHeight();
  }
}
