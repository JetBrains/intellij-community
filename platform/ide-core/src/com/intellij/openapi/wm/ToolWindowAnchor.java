// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm;

import com.intellij.ide.IdeCoreBundle;
import com.intellij.ide.ui.UISettings;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Anchor for positioning {@link ToolWindow tool window} (TOP, LEFT, BOTTOM, RIGHT).
 */
public final class ToolWindowAnchor {
  public static final @NotNull ToolWindowAnchor TOP = new ToolWindowAnchor("top", "action.text.anchor.top", "action.text.anchor.top.capitalized");
  public static final @NotNull ToolWindowAnchor LEFT = new ToolWindowAnchor("left", "action.text.anchor.left", "action.text.anchor.left.capitalized");
  public static final @NotNull ToolWindowAnchor BOTTOM = new ToolWindowAnchor("bottom", "action.text.anchor.bottom", "action.text.anchor.bottom.capitalized");
  public static final @NotNull ToolWindowAnchor RIGHT = new ToolWindowAnchor("right", "action.text.anchor.right", "action.text.anchor.right.capitalized");

  private final @NotNull String myText;

  private final @NotNull String bundleKey;
  private final @NotNull String capitalizedBundleKey;

  private ToolWindowAnchor(@NotNull String text,
                           @NotNull String bundleKey,
                           @NotNull String capitalizedBundleKey){
    this.bundleKey = bundleKey;
    this.capitalizedBundleKey = capitalizedBundleKey;
    myText = text;
  }

  public @NotNull String toString() {
    return myText;
  }

  public @NotNull @Nls String getDisplayName() {
    return IdeCoreBundle.message(bundleKey);
  }

  public @NotNull @Nls String getCapitalizedDisplayName() {
    return IdeCoreBundle.message(capitalizedBundleKey);
  }

  public boolean isHorizontal() {
    return this == TOP || this == BOTTOM;
  }

  public static @NotNull ToolWindowAnchor get(@MagicConstant(intValues = {SwingConstants.CENTER, SwingConstants.TOP, SwingConstants.LEFT, SwingConstants.BOTTOM, SwingConstants.RIGHT}) int swingOrientationConstant) {
    return switch (swingOrientationConstant) {
      case SwingConstants.TOP -> TOP;
      case SwingConstants.BOTTOM -> BOTTOM;
      case SwingConstants.LEFT -> LEFT;
      case SwingConstants.RIGHT -> RIGHT;
      default -> throw new IllegalArgumentException("Unknown anchor constant: " + swingOrientationConstant);
    };
  }

  public boolean isSplitVertically() {
    return this == LEFT && !UISettings.getInstance().getLeftHorizontalSplit()
           || this == RIGHT && !UISettings.getInstance().getRightHorizontalSplit();
  }

  public static @NotNull ToolWindowAnchor fromText(@NotNull String anchor) {
    return switch (anchor) {
      case "top" -> TOP;
      case "left" -> LEFT;
      case "bottom" -> BOTTOM;
      case "right" -> RIGHT;
      default -> throw new IllegalArgumentException("Unknown anchor constant: " + anchor);
    };
  }
}
