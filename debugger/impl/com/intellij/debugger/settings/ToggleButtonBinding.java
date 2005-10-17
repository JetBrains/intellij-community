/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public class ToggleButtonBinding extends FieldDataBinding{
  private final JToggleButton myToggleButton;

  public ToggleButtonBinding(@NonNls String dataFieldName, JToggleButton checkBox) {
    super(dataFieldName);
    myToggleButton = checkBox;
  }

  public void doLoadData(Object from, Field field) throws IllegalAccessException {
    final Boolean value = (Boolean)field.get(from);
    myToggleButton.setSelected(value.booleanValue());
  }

  public void doSaveData(Object to, Field field) throws IllegalAccessException{
    field.set(to, myToggleButton.isSelected()? Boolean.TRUE : Boolean.FALSE);
  }

  protected boolean isModified(Object obj, Field field) throws IllegalAccessException {
    final Boolean value = (Boolean)field.get(obj);
    return myToggleButton.isSelected() != value.booleanValue();
  }
}
