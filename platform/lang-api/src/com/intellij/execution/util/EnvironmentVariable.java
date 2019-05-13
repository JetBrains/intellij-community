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

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

public class EnvironmentVariable implements JDOMExternalizable, Cloneable {
  public String NAME;
  public String VALUE;
  public boolean IS_PREDEFINED;

  public EnvironmentVariable(@NonNls String name, @NonNls String value, boolean isPredefined) {
    NAME = name;
    VALUE = value;
    IS_PREDEFINED = isPredefined;
  }

  public EnvironmentVariable() {
  }

  public void setName(String name) {
    NAME = name;
  }

  public void setValue(String value) {
    VALUE = value;
  }

  public String getName() {
    return NAME;
  }

  public String getValue() {
    return VALUE;
  }

  @Nullable
  public String getDescription() {
    return null;
  }

  public boolean getIsPredefined() {
    return IS_PREDEFINED;
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isVisible() {
    return true;
  }

  @Override
  public EnvironmentVariable clone() {
    try {
      return (EnvironmentVariable)super.clone();
    }
    catch (CloneNotSupportedException e) {
      return null;
    }
  }

  public boolean getNameIsWriteable() {
    return true;
  }
}