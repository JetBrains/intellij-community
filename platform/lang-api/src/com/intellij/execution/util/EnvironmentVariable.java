// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.util;

import com.intellij.openapi.util.*;
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

  public @Nullable @NlsContexts.Tooltip String getDescription() {
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