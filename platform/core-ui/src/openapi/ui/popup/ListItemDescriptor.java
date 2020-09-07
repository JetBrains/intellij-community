// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public interface ListItemDescriptor<T> {
  @NlsContexts.ListItem @Nullable String getTextFor(T value);

  @NlsContexts.Tooltip @Nullable String getTooltipFor(T value);

  @Nullable Icon getIconFor(T value);

  default Icon getSelectedIconFor(T value) {
    return getIconFor(value);
  }

  boolean hasSeparatorAboveOf(T value);

  @NlsContexts.Separator @Nullable String getCaptionAboveOf(T value);
}
