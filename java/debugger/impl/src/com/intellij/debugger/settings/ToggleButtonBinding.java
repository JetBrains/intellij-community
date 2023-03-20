// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.settings;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
 */
public class ToggleButtonBinding extends FieldDataBinding {
  private final JToggleButton myToggleButton;

  public ToggleButtonBinding(@NonNls String dataFieldName, JToggleButton checkBox) {
    super(dataFieldName);
    myToggleButton = checkBox;
  }

  @Override
  public void doLoadData(Object from, Field field) throws IllegalAccessException {
    final Boolean value = (Boolean)field.get(from);
    myToggleButton.setSelected(value.booleanValue());
  }

  @Override
  public void doSaveData(Object to, Field field) throws IllegalAccessException {
    field.set(to, myToggleButton.isSelected());
  }

  @Override
  protected boolean isModified(Object obj, Field field) throws IllegalAccessException {
    final Boolean value = (Boolean)field.get(obj);
    return myToggleButton.isSelected() != value.booleanValue();
  }
}
