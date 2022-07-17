// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/** @deprecated useless, go for {@link ColoredListCellRenderer} directly */
@Deprecated(forRemoval = true)
public abstract class ColoredListCellRendererWrapper<T> extends ColoredListCellRenderer<T> {
  @Override
  protected final void customizeCellRenderer(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
    doCustomize(list, value, index, selected, hasFocus);
  }

  protected abstract void doCustomize(JList list, T value, int index, boolean selected, boolean hasFocus);

  public void append(@NotNull SimpleColoredText text) {
    text.appendToComponent(this);
  }
}