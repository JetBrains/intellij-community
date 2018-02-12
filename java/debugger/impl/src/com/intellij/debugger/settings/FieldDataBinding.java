/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

  public final void loadData(Object from) {
    try {
      final Field field = findField(from);
      doLoadData(from, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }


  public final void saveData(Object to) {
    try {
      final Field field = findField(to);
      doSaveData(to, field);
    }
    catch (IllegalAccessException e) {
      throw new RuntimeException(e.getMessage(), e);
    }
  }

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
