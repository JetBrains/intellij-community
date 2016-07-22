/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.ui.popup.util;

import com.intellij.openapi.ui.popup.ListPopupStep;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.ui.popup.PopupStep;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class BaseListPopupStep<T> extends BaseStep<T> implements ListPopupStep<T> {
  private String myTitle;
  private List<T> myValues;
  private List<Icon> myIcons;
  private int myDefaultOptionIndex = -1;

  public BaseListPopupStep(@Nullable String title, T... values) {
    this(title, values, new Icon[]{});
  }

  public BaseListPopupStep(@Nullable String title, List<? extends T> values) {
    this(title, values, new ArrayList<>());
  }

  public BaseListPopupStep(@Nullable String title, T[] values, Icon[] icons) {
    this(title, Arrays.asList(values), Arrays.asList(icons));
  }

  public BaseListPopupStep(@Nullable String aTitle, @NotNull List<? extends T> aValues, Icon aSameIcon) {
    List<Icon> icons = new ArrayList<>();
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < aValues.size(); i++) {
      icons.add(aSameIcon);
    }
    init(aTitle, aValues, icons);
  }

  public BaseListPopupStep(@Nullable String title, @NotNull List<? extends T> values, List<Icon> icons) {
    init(title, values, icons);
  }

  protected BaseListPopupStep() { }

  protected final void init(@Nullable String title, @NotNull List<? extends T> values, @Nullable List<Icon> icons) {
    myTitle = title;
    myValues = new ArrayList<>(values);
    myIcons = icons;
  }

  @Nullable
  public final String getTitle() {
    return myTitle;
  }

  @NotNull
  public final List<T> getValues() {
    return myValues;
  }

  public PopupStep onChosen(T selectedValue, final boolean finalChoice) {
    return FINAL_CHOICE;
  }

  public Icon getIconFor(T value) {
    int index = myValues.indexOf(value);
    if (index != -1 && myIcons != null && index < myIcons.size()) {
      return myIcons.get(index);
    }
    else {
      return null;
    }
  }

  @Nullable
  public Color getBackgroundFor(T value) {
    return null;
  }

  @Nullable
  public Color getForegroundFor(T value) {
    return null;
  }

  @NotNull
  public String getTextFor(T value) {
    return value.toString();
  }

  @Nullable
  public ListSeparator getSeparatorAbove(T value) {
    return null;
  }

  public boolean isSelectable(T value) {
    return true;
  }

  public boolean hasSubstep(T selectedValue) {
    return false;
  }

  public void canceled() {
  }

  public void setDefaultOptionIndex(int aDefaultOptionIndex) {
    myDefaultOptionIndex = aDefaultOptionIndex;
  }

  public int getDefaultOptionIndex() {
    return myDefaultOptionIndex;
  }
}
