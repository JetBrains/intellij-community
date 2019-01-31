/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.ui;

import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Typed version of {@link ColoredListCellRenderer}.
 *
 * @deprecated useless, go for {@link ColoredListCellRenderer} directly
 */
@Deprecated
public abstract class ColoredListCellRendererWrapper<T> extends ColoredListCellRenderer {
  @Override
  protected final void customizeCellRenderer(@NotNull JList list, Object value, int index, boolean selected, boolean hasFocus) {
    @SuppressWarnings("unchecked") final T t = (T)value;
    doCustomize(list, t, index, selected, hasFocus);
  }

  protected abstract void doCustomize(JList list, T value, int index, boolean selected, boolean hasFocus);

  public void append(@NotNull SimpleColoredText text) {
    text.appendToComponent(this);
  }
}
