// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.settings;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NonNls;

import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
 */
public abstract class FieldDataBinding implements DataBinding{

  private final String myFieldName;

  protected FieldDataBinding(@NonNls String fieldName) {
    myFieldName = fieldName;
  }

  @Override
  public final void loadData(Object from) {
    try {
      final Field field = findField(from);
      doLoadData(from, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }


  @Override
  public final void saveData(Object to) {
    try {
      final Field field = findField(to);
      doSaveData(to, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  @Override
  public boolean isModified(Object obj) {
    try {
      final Field field = findField(obj);
      return isModified(obj, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

  protected abstract void doLoadData(Object from, Field field) throws IllegalAccessException;

  protected abstract void doSaveData(Object to, Field field) throws IllegalAccessException;

  protected abstract boolean isModified(Object obj, Field field) throws IllegalAccessException;

  private Field findField(Object from) {
    try {
      return ReflectionUtil.findField(from.getClass(), null, myFieldName);
    }
    catch (NoSuchFieldException e) {
      throw new RuntimeException(DebuggerBundle.message("error.field.not.found.in.class", myFieldName, from.getClass().getName()));
    }
  }
}
