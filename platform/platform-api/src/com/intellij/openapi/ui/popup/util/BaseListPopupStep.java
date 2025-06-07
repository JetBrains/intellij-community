// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import com.intellij.openapi.util.NlsContexts.PopupTitle;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseListPopupStep<T> extends BaseStep<T> implements ListPopupStep<T> {
  private @PopupTitle String myTitle;
  private List<T> myValues;
  private List<? extends Icon> myIcons;
  private int myDefaultOptionIndex = -1;

  @SafeVarargs
  public BaseListPopupStep(@PopupTitle @Nullable String title, T @NotNull ... values) {
    this(title, values, new Icon[]{});
  }

  public BaseListPopupStep(@PopupTitle @Nullable String title, List<? extends T> values) {
    this(title, values, new ArrayList<>());
  }

  public BaseListPopupStep(@PopupTitle @Nullable String title, T[] values, Icon[] icons) {
    this(title, Arrays.asList(values), Arrays.asList(icons));
  }

  public BaseListPopupStep(@PopupTitle @Nullable String title, @NotNull List<? extends T> aValues, Icon aSameIcon) {
    List<Icon> icons = new ArrayList<>();
    for (int i = 0; i < aValues.size(); i++) {
      icons.add(aSameIcon);
    }
    init(title, aValues, icons);
  }

  public BaseListPopupStep(@PopupTitle @Nullable String title, @NotNull List<? extends T> values, List<? extends Icon> icons) {
    init(title, values, icons);
  }

  protected BaseListPopupStep() { }

  protected final void init(@PopupTitle @Nullable String title, @NotNull List<? extends T> values, @Nullable List<? extends Icon> icons) {
    myTitle = title;
    myValues = new ArrayList<>(values);
    myIcons = icons;
  }

  @Override
  public final @Nullable String getTitle() {
    return myTitle;
  }

  @Override
  public final @NotNull List<T> getValues() {
    return myValues;
  }

  @Override
  public @Nullable PopupStep<?> onChosen(T selectedValue, boolean finalChoice) {
    return FINAL_CHOICE;
  }

  @Override
  public Icon getIconFor(T value) {
    int index = myValues.indexOf(value);
    if (index != -1 && myIcons != null && index < myIcons.size()) {
      return myIcons.get(index);
    }
    else {
      return null;
    }
  }

  public @Nullable Color getBackgroundFor(T value) {
    return null;
  }

  public @Nullable Color getForegroundFor(@SuppressWarnings("unused") T value) {
    return null;
  }

  @Override
  public @NotNull String getTextFor(T value) {
    //noinspection HardCodedStringLiteral (can't be fixed without upgrading the inspection or breaking clients)
    return value.toString();
  }

  @Override
  public @Nullable ListSeparator getSeparatorAbove(T value) {
    return null;
  }

  @Override
  public boolean isSelectable(T value) {
    return true;
  }

  @Override
  public boolean hasSubstep(T selectedValue) {
    return false;
  }

  @Override
  public void canceled() { }

  public void setDefaultOptionIndex(int aDefaultOptionIndex) {
    myDefaultOptionIndex = aDefaultOptionIndex;
  }

  @Override
  public int getDefaultOptionIndex() {
    return myDefaultOptionIndex;
  }

  @ApiStatus.Internal
  public boolean isLazyUiSnapshot() {
    return false;
  }
}
