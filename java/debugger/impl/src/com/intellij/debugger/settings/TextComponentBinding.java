// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import org.jetbrains.annotations.NonNls;

import javax.swing.text.JTextComponent;
import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
 */
public class TextComponentBinding extends FieldDataBinding{
  private final JTextComponent myTextComponent;

  public TextComponentBinding(@NonNls String dataFieldName, JTextComponent textComponent) {
    super(dataFieldName);
    myTextComponent = textComponent;
  }

  @Override
  public void doLoadData(Object from, Field field) throws IllegalAccessException {
    final String value = (String)field.get(from);
    myTextComponent.setText(value);
  }

  @Override
  public void doSaveData(Object to, Field field) throws IllegalAccessException{
    field.set(to, myTextComponent.getText().trim());
  }

  @Override
  protected boolean isModified(Object obj, Field field) throws IllegalAccessException {
    final String value = (String)field.get(obj);
    return myTextComponent.getText().trim().equals(value);
  }
}
