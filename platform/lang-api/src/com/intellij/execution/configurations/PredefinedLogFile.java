/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.execution.configurations;

import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PredefinedLogFile implements JDOMExternalizable {
  @NonNls private static final String ID_ATTRIBUTE = "id";
  @NonNls private static final String ENABLED_ATTRIBUTE = "enabled";
  private String myId;
  private boolean myEnabled;

  public PredefinedLogFile() {
  }


  public PredefinedLogFile(PredefinedLogFile logFile) {
    myEnabled = logFile.myEnabled;
    myId = logFile.myId;
  }

  public PredefinedLogFile(final @NotNull @NonNls String id, final boolean enabled) {
    myEnabled = enabled;
    myId = id;
  }

  public boolean isEnabled() {
    return myEnabled;
  }

  public void setEnabled(final boolean enabled) {
    myEnabled = enabled;
  }

  public String getId() {
    return myId;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final PredefinedLogFile that = (PredefinedLogFile)o;
    return myId.equals(that.myId);
  }

  public int hashCode() {
    return myId.hashCode();
  }


  @Override
  public void readExternal(Element element) throws InvalidDataException {
    myId = element.getAttributeValue(ID_ATTRIBUTE);
    myEnabled = Boolean.parseBoolean(element.getAttributeValue(ENABLED_ATTRIBUTE));
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    element.setAttribute(ID_ATTRIBUTE, myId);
    element.setAttribute(ENABLED_ATTRIBUTE, String.valueOf(myEnabled));
  }
}
