// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui;

import com.intellij.icons.AllIcons;
import com.intellij.ui.ExperimentalUI;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.icons.IconReplacer;
import com.intellij.ui.icons.IconWithOverlay;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.Shape;
import java.awt.geom.Rectangle2D;
import java.util.Objects;

/**
 * @author Konstantin Bulenkov
 */
@ApiStatus.Internal
public final class InvalidRunConfigurationIcon extends IconWithOverlay {
  public InvalidRunConfigurationIcon(@NotNull Icon runConfigurationIcon) {
    this(runConfigurationIcon, AllIcons.RunConfigurations.InvalidConfigurationLayer);
  }

  private InvalidRunConfigurationIcon(@NotNull Icon runConfigurationIcon, @NotNull Icon invalidConfigurationLayer) {
    super(runConfigurationIcon, invalidConfigurationLayer);
  }

  @Override
  public @Nullable Shape getOverlayShape(int x, int y) {
    if (ExperimentalUI.isNewUI()) {
      // Using getScale() won't do here, because it only handles the case when the icon scaled by changing its OBJ_SCALE.
      // But there are other ways to "scale" an icon, e.g. using replaceBy() and a replacer that substitutes an icon of a different size.
      // It could be a custom SVG file optimized for a larger size, for example.
      // Because this thing is only for one place in the platform, and the shape calculation assumes the size of 16x16 anyway,
      // we can use "poor man's getScale()" here to compute the effective scale.
      float scale = (float) getIconWidth() / 16.0f;
      return new Rectangle2D.Float(x + scale * (16 - 7), y, 7 * scale, 7 * scale);
    }
    return null;
  }

  @Override
  public @NotNull InvalidRunConfigurationIcon replaceBy(@NotNull IconReplacer replacer) {
    return new InvalidRunConfigurationIcon(replacer.replaceIcon(Objects.requireNonNull(getIcon(0))),
                                           replacer.replaceIcon(Objects.requireNonNull(getIcon(1))));
  }

  @Override
  public @NotNull LayeredIcon copy() {
    return new InvalidRunConfigurationIcon(Objects.requireNonNull(getIcon(0)), Objects.requireNonNull(getIcon(1)));
  }
}
