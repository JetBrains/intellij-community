// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.StatusText;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.Icon;
import java.awt.event.InputEvent;

public interface ListPopupStepEx<T> extends ListPopupStep<T> {

  default @Nullable PopupStep<?> onChosen(T selectedValue, boolean finalChoice, @Nullable InputEvent inputEvent) {
    return onChosen(selectedValue, finalChoice, inputEvent == null ? 0 : inputEvent.getModifiers());
  }

  /** @deprecated Override {@link #onChosen(Object, boolean, InputEvent)} instead */
  @Deprecated(forRemoval = true)
  default @Nullable PopupStep<?> onChosen(T selectedValue,
                                          boolean finalChoice,
                                          @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers) {
    return onChosen(selectedValue, finalChoice);
  }

  @NlsContexts.Tooltip @Nullable String getTooltipTextFor(T value);

  void setEmptyText(@NotNull StatusText emptyText);

  default @Nls @Nullable String getSecondaryTextFor(T t) { return null; }

  default @Nls @Nullable Icon getSecondaryIconFor(T t) { return null; }
}
