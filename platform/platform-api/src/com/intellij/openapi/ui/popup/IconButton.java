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

import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IconButton extends ActiveIcon {

  private final String myTooltip;

  private Icon myHovered;

  public IconButton(final String tooltip, @Nullable final Icon regular, @Nullable final Icon hovered, @Nullable final Icon inactive) {
    super(regular, inactive);
    myTooltip = tooltip;
    setHovered(hovered);
  }

  private void setHovered(final Icon hovered) {
    myHovered = hovered != null ? hovered : getRegular();
  }

  public IconButton(final String tooltip, final Icon regular, final Icon hovered) {
    this(tooltip, regular, hovered, regular);
  }

  public IconButton(final String tooltip, final Icon regular) {
    this(tooltip, regular, regular, regular);
  }


  protected void setIcons(@Nullable final Icon regular, @Nullable final Icon inactive, @Nullable final Icon hovered) {
    setIcons(regular, inactive);
    setHovered(hovered);
  }

  public Icon getHovered() {
    return myHovered;
  }

  public String getTooltip() {
    return myTooltip;
  }
}
