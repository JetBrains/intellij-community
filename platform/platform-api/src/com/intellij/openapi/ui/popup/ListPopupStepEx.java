package com.intellij.openapi.ui.popup;

import org.intellij.lang.annotations.MagicConstant;

import java.awt.event.InputEvent;

public interface ListPopupStepEx<T> extends ListPopupStep<T> {
  PopupStep onChosen(T selectedValue, boolean finalChoice, @MagicConstant(flagsFromClass = InputEvent.class) int eventModifiers);
}