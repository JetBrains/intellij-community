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

import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.lang.reflect.Field;

/**
 * @author Eugene Zhuravlev
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
    field.set(to, myToggleButton.isSelected());
  }

  protected boolean isModified(Object obj, Field field) throws IllegalAccessException {
    final Boolean value = (Boolean)field.get(obj);
    return myToggleButton.isSelected() != value.booleanValue();
  }
}
