// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.impl;

import com.intellij.openapi.editor.Document;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeSupport;

/**
 * Allows creating PropertyChangeSupport without passing the document to the constructor
 */
final class DocumentPropertyChangeSupport extends PropertyChangeSupport {
  private static final Object FAKE_PROPERTY_SOURCE = new Object();

  DocumentPropertyChangeSupport() {
    super(FAKE_PROPERTY_SOURCE);
  }

  /**
   * Forbidden operation, use {@link #firePropertyChange(Document, String, boolean, boolean)}
   */
  @Override
  public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    throw new UnsupportedOperationException();
  }

  /** @noinspection SameParameterValue*/
  void firePropertyChange(
    @NotNull Document hostDocument,
    @NotNull String propertyName,
    boolean oldValue,
    boolean newValue
  ) {
    if (oldValue != newValue) {
      firePropertyChange(new PropertyChangeEvent(hostDocument, propertyName, oldValue, newValue));
    }
  }
}
