package com.intellij.openapi.ui.popup;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ui.StatusText;
import org.intellij.lang.annotations.MagicConstant;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.event.InputEvent;

public interface ListPopupStepEx<T> extends ListPopupStep<T> {
  PopupStep onChosen(T selectedValue, boolean finalChoice, @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers);

  @Nullable @NlsContexts.Tooltip
  String getTooltipTextFor(T value);
  
  void setEmptyText(@NotNull StatusText emptyText);

  default @Nls @Nullable String getValueFor(T t) { return null; }
}