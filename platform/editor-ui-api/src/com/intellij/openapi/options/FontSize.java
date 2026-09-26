// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options;

import org.jetbrains.annotations.NotNull;

/**
 * Enumerates font size values used by quick documentation component ('Adjust font size...' slider switches between these values).
 * <p/>
 * Note that if user changes global UI font size, quick documentation font will be scaled proportionally.
 */
public enum FontSize {

  XX_SMALL(10), X_SMALL(12), SMALL(13), MEDIUM(14), LARGE(16), X_LARGE(18), XX_LARGE(24);

  private final int    mySize;

  FontSize(int size) {
    mySize = size;
  }

  public int getSize() {
    return mySize;
  }

  /**
   * @return    {@link FontSize} that is one unit large than the current one; current object if it already stands for a maximum size
   */
  public @NotNull FontSize larger() {
    int i = ordinal();
    return i >= values().length - 1 ? this : values()[i + 1];
  }

  /**
   * @return    {@link FontSize} that is one unit smaller than the current one; current object if it already stands for a minimum size
   */
  public @NotNull FontSize smaller() {
    int i = ordinal();
    return i > 0 ? values()[i - 1] : this;
  }
}
