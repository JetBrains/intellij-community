// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.jcef;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.HashMap;
import java.util.Map;

final class PropertyChangeHelper {
  @NotNull private final Map<String, Object> myProperties = new HashMap<>();
  @NotNull private final PropertyChangeSupport myPropertyChangeSupport = new PropertyChangeSupport(this);

  public void setProperty(@NotNull String name, @Nullable Object value) {
    synchronized (myProperties) {
      Object oldValue = myProperties.put(name, value);
      myPropertyChangeSupport.firePropertyChange(name, oldValue, value);
    }
  }

  @Nullable
  public Object getProperty(@NotNull String name) {
    synchronized (myProperties) {
      return myProperties.get(name);
    }
  }

  void addPropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(name, listener);
  }

  void removePropertyChangeListener(@NotNull String name, @NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.removePropertyChangeListener(name, listener);
  }
}
