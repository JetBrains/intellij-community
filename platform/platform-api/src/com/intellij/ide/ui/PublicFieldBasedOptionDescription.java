/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.diagnostic.Logger;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;

/**
 * @author Konstantin Bulenkov
 */
public abstract class PublicFieldBasedOptionDescription extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PublicFieldBasedOptionDescription.class);
  private final String myFieldName;

  public PublicFieldBasedOptionDescription(String option, String configurableId, String fieldName) {
    super(option, configurableId);
    myFieldName = fieldName;
  }

  @NotNull
  public abstract Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    Object instance = getInstance();
    try {
      final Field field = instance.getClass().getField(myFieldName);
      return field.getBoolean(instance);
    }
    catch (NoSuchFieldException | IllegalAccessException ignore) {
      LOG.warn(String.format("Boolean field '%s' not found in %s", myFieldName, instance));
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      final Field field = instance.getClass().getField(myFieldName);
      field.setBoolean(instance, enabled);
    }
    catch (NoSuchFieldException | IllegalAccessException ignore) {
      LOG.warn(String.format("Boolean field '%s' not found in %s", myFieldName, instance));
    }
    fireUpdated();
  }
}
