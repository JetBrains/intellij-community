/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.execution.util;

import com.intellij.ide.util.PropertiesComponent;
import com.intellij.util.config.AbstractProperty;
import com.intellij.util.config.Storage;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;

public class StoringPropertyContainer extends AbstractProperty.AbstractPropertyContainer<AbstractProperty<Boolean>> {
  private final HashMap<AbstractProperty<Boolean>, Boolean> myValues = new HashMap<AbstractProperty<Boolean>, Boolean>();
  private final Storage myStorage;

  public StoringPropertyContainer(@NonNls final String groupName, final PropertiesComponent propertiesComponent) {
    this(new Storage.PropertiesComponentStorage(groupName, propertiesComponent));
  }

  public StoringPropertyContainer(final Storage storage) {
    myStorage = storage;
  }

  public void setIfUndefined(AbstractProperty<Boolean> property, boolean value) {
    if (myStorage.get(property.getName()) == null) {
      setValueOf(property, value);
    }
  }

  @Override
  protected void setValueOf(final AbstractProperty<Boolean> property, final Object value) {
    myValues.put(property, (Boolean)value);
    onPropertyChanged(property, (Boolean)value);
    myStorage.put(property.getName(), stringValue(value));
  }

  private String stringValue(final Object value) {
    return value.toString();
  }

  @Override
  public boolean hasProperty(final AbstractProperty property) {
    return myValues.containsKey(property);
  }

  @Override
  protected Object getValueOf(final AbstractProperty<Boolean> property) {
    Object value = myValues.get(property);
    if (value == null) {
      final String stringValue = myStorage.get(property.getName());
      value = stringValue != null ? parseValue(stringValue) : property.getDefault(this);
      myValues.put(property, (Boolean)value);
    }
    return value;
  }

  private Boolean parseValue(final String stringValue) {
    return Boolean.valueOf(stringValue);
  }

  protected <T> void onPropertyChanged(final AbstractProperty<T> property, final T value) {}
}
