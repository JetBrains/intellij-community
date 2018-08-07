// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.intellij.images.options.impl;

import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.JDOMExternalizer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.JdomKt;
import org.intellij.images.options.ExternalEditorOptions;
import org.jdom.Element;

import java.beans.PropertyChangeSupport;

/**
 * External editor options.
 */
final class ExternalEditorOptionsImpl implements ExternalEditorOptions, JDOMExternalizable {
  private final PropertyChangeSupport propertyChangeSupport;
  private String executablePath;

  public ExternalEditorOptionsImpl(PropertyChangeSupport propertyChangeSupport) {
    this.propertyChangeSupport = propertyChangeSupport;
  }

  public String getExecutablePath() {
    return executablePath;
  }

  void setExecutablePath(String executablePath) {
    String oldValue = this.executablePath;
    if (oldValue != null && !oldValue.equals(executablePath) || oldValue == null && executablePath != null) {
      this.executablePath = executablePath;
      propertyChangeSupport.firePropertyChange(ATTR_EXECUTABLE_PATH, oldValue, this.executablePath);
    }
  }

  public ExternalEditorOptions clone() throws CloneNotSupportedException {
    return (ExternalEditorOptions)super.clone();
  }

  public void inject(ExternalEditorOptions options) {
    setExecutablePath(options.getExecutablePath());
  }

  public boolean setOption(String name, Object value) {
    if (ATTR_EXECUTABLE_PATH.equals(name)) {
      setExecutablePath((String)value);
    }
    else {
      return false;
    }
    return true;
  }

  public void readExternal(Element element) {
    executablePath = JDOMExternalizer.readString(element, ATTR_EXECUTABLE_PATH);
  }

  public void writeExternal(Element element) {
    if (!StringUtil.isEmpty(executablePath)) {
      JdomKt.addOptionTag(element, ATTR_EXECUTABLE_PATH, executablePath, "setting");
    }
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ExternalEditorOptions)) {
      return false;
    }

    ExternalEditorOptions otherOptions = (ExternalEditorOptions)o;

    return executablePath != null ?
           executablePath.equals(otherOptions.getExecutablePath()) :
           otherOptions.getExecutablePath() == null;
  }

  public int hashCode() {
    return executablePath != null ? executablePath.hashCode() : 0;
  }
}
