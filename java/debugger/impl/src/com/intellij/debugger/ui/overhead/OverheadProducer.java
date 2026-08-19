// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui.overhead;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;

public interface OverheadProducer {
  boolean isEnabled();

  void setEnabled(boolean enabled);

  @NotNull Presentation computePresentation();

  record Presentation(@NotNull @Nls String text, @Nullable Icon icon) {
    public Presentation(@NotNull @Nls String text) {
      this(text, null);
    }
  }

  default boolean isObsolete() {
    return false;
  }

  default boolean track() {
    return true;
  }
}
