/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * @author Sergey.Malenkov
 */
public abstract class PublicMethodBasedOptionDescription extends BooleanOptionDescription {
  private final String myGetterName;
  private final String mySetterName;

  public PublicMethodBasedOptionDescription(String option, String configurableId, String getterName, String setterName) {
    super(option, configurableId);
    myGetterName = getterName;
    mySetterName = setterName;
  }

  public abstract Object getInstance();

  protected void fireUpdated() {
  }

  @Override
  public boolean isOptionEnabled() {
    try {
      Method method = getInstance().getClass().getMethod(myGetterName);
      Object object = method.invoke(getInstance());
      return (object instanceof Boolean) && (Boolean)object;
    }
    catch (NoSuchMethodException ignore) {
    }
    catch (IllegalAccessException ignore) {
    }
    catch (InvocationTargetException ignore) {
    }
    return false;
  }

  @Override
  public void setOptionState(boolean enabled) {
    try {
      Method method = getInstance().getClass().getMethod(mySetterName, boolean.class);
      method.invoke(getInstance(), Boolean.valueOf(enabled));
    }
    catch (NoSuchMethodException ignore) {
    }
    catch (IllegalAccessException ignore) {
    }
    catch (InvocationTargetException ignore) {
    }
    fireUpdated();
  }
}
