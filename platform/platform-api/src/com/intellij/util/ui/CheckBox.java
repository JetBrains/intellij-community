// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.event.ItemEvent;

public class CheckBox extends JCheckBox {
  public CheckBox(@NotNull @NlsContexts.Checkbox String label, @NotNull InspectionProfileEntry owner, @NonNls String property) {
    this(label, (Object)owner, property);
  }

  /**
   * @param property field must be non-private (or ensure that it won't be scrambled by other means)
   */
  public CheckBox(@NotNull @NlsContexts.Checkbox String label, @NotNull Object owner, @NonNls String property) {
    super(label, getBooleanPropertyValue(owner, property));
    addItemListener(e -> ReflectionUtil.setField(owner.getClass(), owner, boolean.class, property, e.getStateChange() == ItemEvent.SELECTED));
  }

  private static boolean getBooleanPropertyValue(@NotNull Object owner, @NotNull String property) {
    Boolean value = ReflectionUtil.getField(owner.getClass(), owner, boolean.class, property);
    if (value == null) {
      throw new IllegalArgumentException("Property '" + property + "' not found in " + owner + " (" + owner.getClass() + ")");
    }
    return value;
  }
}