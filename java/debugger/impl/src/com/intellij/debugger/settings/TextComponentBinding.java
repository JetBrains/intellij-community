/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.debugger.settings;

import org.jetbrains.annotations.NonNls;

import javax.swing.text.JTextComponent;
import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
 * Date: Apr 12, 2005
 */
public class TextComponentBinding extends FieldDataBinding{
  private final JTextComponent myTextComponent;

  public TextComponentBinding(@NonNls String dataFieldName, JTextComponent textComponent) {
    super(dataFieldName);
    myTextComponent = textComponent;
  }

  public void doLoadData(Object from, Field field) throws IllegalAccessException {
    final String value = (String)field.get(from);
    myTextComponent.setText(value);
  }

  public void doSaveData(Object to, Field field) throws IllegalAccessException{
    field.set(to, myTextComponent.getText().trim());
  }

  protected boolean isModified(Object obj, Field field) throws IllegalAccessException {
    final String value = (String)field.get(obj);
    return myTextComponent.getText().trim().equals(value);
  }
}
