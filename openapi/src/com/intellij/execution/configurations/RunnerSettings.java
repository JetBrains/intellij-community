/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;

public final class RunnerSettings<RSettings extends JDOMExternalizable> implements JDOMExternalizable {
  private final RSettings myData;
  private final RunProfile myConfig;

  public RunnerSettings(RSettings data, RunProfile config) {
    myData = data;
    myConfig = config;
  }

  public RSettings getData() {
    return myData;
  }

  public RunProfile getRunProfile() {
    return myConfig;
  }

  public void readExternal(Element element) throws InvalidDataException {
    if (myData != null) {
      myData.readExternal(element);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    if (myData != null) {
      myData.writeExternal(element);
    }
  }
}