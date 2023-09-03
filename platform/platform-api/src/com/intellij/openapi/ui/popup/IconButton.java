// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts.Tooltip;
import com.intellij.ui.icons.IconReplacer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IconButton extends ActiveIcon {

  private final @Tooltip String myTooltip;

  private Icon myHovered;

  public IconButton(@Tooltip String tooltip, final @Nullable Icon regular, final @Nullable Icon hovered, final @Nullable Icon inactive) {
    super(regular, inactive);
    myTooltip = tooltip;
    setHovered(hovered);
  }

  private IconButton(@Tooltip String tooltip, final @NotNull ActiveIcon base, final @Nullable Icon hovered) {
    super(base);
    myTooltip = tooltip;
    setHovered(hovered);
  }

  private void setHovered(final Icon hovered) {
    myHovered = hovered != null ? hovered : getRegular();
  }

  public IconButton(@Tooltip String tooltip, final Icon regular, final Icon hovered) {
    this(tooltip, regular, hovered, regular);
  }

  public IconButton(@Tooltip String tooltip, final Icon regular) {
    this(tooltip, regular, regular, regular);
  }

  @ApiStatus.Internal
  public void setIcons(final @Nullable Icon regular, final @Nullable Icon inactive, final @Nullable Icon hovered) {
    setIcons(regular, inactive);
    setHovered(hovered);
  }

  public Icon getHovered() {
    return myHovered;
  }

  public @Tooltip String getTooltip() {
    return myTooltip;
  }

  @Override
  public @NotNull IconButton replaceBy(@NotNull IconReplacer replacer) {
    return new IconButton(myTooltip, super.replaceBy(replacer), replacer.replaceIcon(myHovered));
  }
}
