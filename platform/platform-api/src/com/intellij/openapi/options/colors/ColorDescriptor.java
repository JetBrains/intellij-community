// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.colors;

import com.intellij.openapi.editor.colors.ColorKey;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

/**
 * Describes a color which can be configured in a custom colors and fonts page.
 *
 * @see ColorSettingsPage#getColorDescriptors()
 */
public final class ColorDescriptor extends AbstractKeyDescriptor<ColorKey> {
  public static final ColorDescriptor[] EMPTY_ARRAY = new ColorDescriptor[0];

  public enum Kind {
    BACKGROUND,
    FOREGROUND,
    BACKGROUND_WITH_TRANSPARENCY,
    FOREGROUND_WITH_TRANSPARENCY;

    public boolean isBackground() {
      return this == BACKGROUND || this == BACKGROUND_WITH_TRANSPARENCY;
    }

    public boolean isForeground() {
      return this == FOREGROUND || this == FOREGROUND_WITH_TRANSPARENCY;
    }

    public boolean isWithTransparency() {
      return this == FOREGROUND_WITH_TRANSPARENCY || this == BACKGROUND_WITH_TRANSPARENCY;
    }
  }

  private final Kind myKind;

  /** Please use {@link #ColorDescriptor(Supplier, ColorKey, Kind)} instead. */
  public ColorDescriptor(@NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String displayName,
                         @NotNull ColorKey key,
                         @NotNull Kind kind) {
    this(new StaticSupplier(displayName), key, kind);
  }

  /**
   * Creates a color descriptor with the specified name and color key.
   *
   * @param displayName the name of the color shown in the colors list.
   * @param key         the color key for which the color is specified.
   * @param kind        the type of color corresponding to the color key (foreground or background).
   */
  public ColorDescriptor(@NotNull Supplier<@Nls(capitalization = Nls.Capitalization.Sentence) String> displayName,
                         @NotNull ColorKey key,
                         @NotNull Kind kind) {
    super(displayName, key);
    myKind = kind;
  }

  /**
   * Returns the type of color corresponding to the color key (foreground or background).
   */
  public @NotNull Kind getKind() {
    return myKind;
  }

  @SuppressWarnings("RedundantMethodOverride") // binary compatibility
  @Override
  public @NotNull ColorKey getKey() {
    return super.getKey();
  }
}
