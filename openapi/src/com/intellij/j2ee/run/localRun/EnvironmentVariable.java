/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.j2ee.run.localRun;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public class EnvironmentVariable implements JDOMExternalizable {
  public String NAME;
  public String VALUE;
  public boolean IS_PREDEFINED;

  public EnvironmentVariable(String name, String value, boolean isPredefined) {
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

  public boolean getIsPredefined() {
    return IS_PREDEFINED;
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public boolean isVisible() {
    return true;
  }

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