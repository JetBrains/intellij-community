// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * @author Alexander Lobas
 */
public interface ConfigurableTreeRenderer {
  @Nullable
  Pair<Component, Layout> getDecorator(@NotNull JComponent tree, @Nullable UnnamedConfigurable configurable, boolean selected);

  interface Layout {
    void layoutBeforePaint(@NotNull Component renderer,
                           @NotNull Rectangle bounds,
                           @NotNull Rectangle text,
                           @NotNull Rectangle right,
                           int textBaseline);
  }
}
