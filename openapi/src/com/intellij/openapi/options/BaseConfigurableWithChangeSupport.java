/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.options;

import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.util.ArrayList;

public abstract class BaseConfigurableWithChangeSupport extends BaseConfigurable {
  private ArrayList<ChangeListener> myListeners = new ArrayList<ChangeListener>();

  public void setModified(final boolean modified) {
    fireStateChanged();
    super.setModified(modified);
  }

  public void addChangeListener(final ChangeListener listener) {
    myListeners.add(listener);
  }

  public void fireStateChanged() {
    final ChangeEvent event = new ChangeEvent(this);
    final ChangeListener[] listeners = myListeners.toArray(new ChangeListener[myListeners.size()]);
    for (int i = 0; i < listeners.length; i++) {
      final ChangeListener listener = listeners[i];
      listener.stateChanged(event);
    }
  }
}