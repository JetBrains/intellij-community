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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Sergey.Malenkov
 */
public abstract class PublicMethodBasedOptionDescription extends BooleanOptionDescription {
  private static final Logger LOG = Logger.getInstance(PublicMethodBasedOptionDescription.class);

  private final String myGetterName;
  private final String mySetterName;

  public PublicMethodBasedOptionDescription(String option, String configurableId, String getterName, String setterName) {
    super(option, configurableId);
    myGetterName = getterName;
    mySetterName = setterName;
  }

  @NotNull
  public abstract Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    Object instance = getInstance();
    try {
      Method method = instance.getClass().getMethod(myGetterName);
      Object object = method.invoke(instance);
      return (object instanceof Boolean) && (Boolean)object;
    }
    catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ignore) {
      LOG.error(String.format("Method '%s' not found in %s", myGetterName, instance));
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    Object instance = getInstance();
    try {
      Method method = instance.getClass().getMethod(mySetterName, boolean.class);
      method.invoke(instance, Boolean.valueOf(enabled));
    }
    catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ignore) {
      LOG.error(String.format("Method '%s' not found in %s", mySetterName, instance));
    }
    fireUpdated();
  }
}
