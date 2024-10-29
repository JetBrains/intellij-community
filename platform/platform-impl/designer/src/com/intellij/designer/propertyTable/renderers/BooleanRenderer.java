// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.designer.propertyTable.renderers;

import com.intellij.designer.model.PropertiesContainer;
import com.intellij.designer.model.PropertyContext;
import com.intellij.designer.propertyTable.PropertyRenderer;
import com.intellij.designer.propertyTable.PropertyTable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public final class BooleanRenderer extends JCheckBox implements PropertyRenderer {
  @Override
  public @NotNull JComponent getComponent(@Nullable PropertiesContainer container,
                                          PropertyContext context,
                                          @Nullable Object value,
                                          boolean selected,
                                          boolean hasFocus) {
    PropertyTable.updateRenderer(this, selected);
    setSelected(value != null && (Boolean)value);
    return this;
  }
}