// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.EnumComboBoxModel;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public final class PresentableEnumUtil {
  private PresentableEnumUtil() {}

  public static <T extends Enum<T> & PresentableEnum> JComboBox<T> fill(JComboBox<T> comboBox, Class<T> enumClass) {
    comboBox.setModel(new EnumComboBoxModel<>(enumClass));
    comboBox.setRenderer(new PresentableEnumCellRenderer<>());
    return comboBox;
  }

  public static <T extends Enum<T> & PresentableEnum> ComboBox<T> fill(ComboBox<T> comboBox, Class<T> enumClass) {
    comboBox.setModel(new EnumComboBoxModel<>(enumClass));
    comboBox.setRenderer(new PresentableEnumCellRenderer<>());
    return comboBox;
  }
}

class PresentableEnumCellRenderer<T extends Enum<T> & PresentableEnum> extends SimpleListCellRenderer<T> {
  @Override
  public void customize(@NotNull JList<? extends T> list, T value, int index, boolean selected, boolean hasFocus) {
    setText(value.getPresentableText());
  }
}