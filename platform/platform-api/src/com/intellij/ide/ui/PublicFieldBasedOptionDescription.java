/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.ui;

import com.intellij.ide.ui.search.BooleanOptionDescription;

import java.lang.reflect.Field;

/**
 * @author Konstantin Bulenkov
 */
public abstract class PublicFieldBasedOptionDescription extends BooleanOptionDescription {
  private final String myFieldName;

  public PublicFieldBasedOptionDescription(String option, String configurableId, String fieldName) {
    super(option, configurableId);
    myFieldName = fieldName;
  }

  public abstract Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    try {
      final Field field = getInstance().getClass().getField(myFieldName);
      return field.getBoolean(getInstance());
    }
    catch (NoSuchFieldException ignore) {
    }
    catch (IllegalAccessException ignore) {
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    try {
      final Field field = getInstance().getClass().getField(myFieldName);
      field.setBoolean(getInstance(), enabled);
    }
    catch (NoSuchFieldException ignore) {
    }
    catch (IllegalAccessException ignore) {
    }
    fireUpdated();
  }
}
