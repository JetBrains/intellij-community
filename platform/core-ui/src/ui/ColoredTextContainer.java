// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.util.NlsContexts.Label;
import com.intellij.openapi.util.NlsContexts.Tooltip;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ColoredTextContainer {

  void append(@NotNull @Label String fragment, @NotNull SimpleTextAttributes attributes);

  default void append(@NotNull @Label String fragment, @NotNull SimpleTextAttributes attributes, Object tag) {
    append(fragment, attributes);
  }

  default void setIcon(@Nullable Icon icon) {}

  default void setToolTipText(@Nullable @Tooltip String text) {}

  default void append(@NotNull ColoredText coloredText) {
    for (ColoredText.Fragment fragment : coloredText.fragments()) {
      append(fragment.fragmentText(), fragment.fragmentAttributes());
    }
  }
}
