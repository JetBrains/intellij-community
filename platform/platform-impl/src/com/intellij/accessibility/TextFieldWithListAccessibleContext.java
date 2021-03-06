// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.accessibility;

import javax.accessibility.*;
import javax.swing.text.JTextComponent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

public class TextFieldWithListAccessibleContext extends JTextComponent.AccessibleJTextComponent {

  private final AccessibleContext myListContext;

  private PropertyChangeSupport accessibleChangeSupport = null;

  public TextFieldWithListAccessibleContext(JTextComponent textComponent, AccessibleContext listContext) {
    textComponent.super();
    myListContext = listContext;
  }

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    if (accessibleChangeSupport == null) {
      accessibleChangeSupport = new PropertyChangeSupport(this);
      super.addPropertyChangeListener(evt -> redirectEvent(evt));
      myListContext.addPropertyChangeListener(evt -> redirectEvent(evt));
    }
    accessibleChangeSupport.addPropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    if (accessibleChangeSupport != null) {
      accessibleChangeSupport.removePropertyChangeListener(listener);
    }
  }

  private void redirectEvent(PropertyChangeEvent evt) {
    firePropertyChange(evt.getPropertyName(), evt.getOldValue(), evt.getNewValue());
  }

  @Override
  public void firePropertyChange(String propertyName,
                                 Object oldValue,
                                 Object newValue) {
    if (accessibleChangeSupport != null) {
      if (newValue instanceof PropertyChangeEvent) {
        PropertyChangeEvent pce = (PropertyChangeEvent)newValue;
        accessibleChangeSupport.firePropertyChange(pce);
      } else {
        accessibleChangeSupport.firePropertyChange(propertyName,
                                                   oldValue,
                                                   newValue);
      }
    }
  }

  @Override
  public AccessibleStateSet getAccessibleStateSet() {
    AccessibleStateSet set = super.getAccessibleStateSet();
    set.addAll(myListContext.getAccessibleStateSet().toArray());
    return set;
  }

  @Override
  public int getAccessibleChildrenCount() {
    return myListContext.getAccessibleChildrenCount();
  }

  @Override
  public Accessible getAccessibleChild(int i) {
    return myListContext.getAccessibleChild(i);
  }

  @Override
  public AccessibleSelection getAccessibleSelection() {
    return myListContext.getAccessibleSelection();
  }

  @Override
  public AccessibleTable getAccessibleTable() {
    return myListContext.getAccessibleTable();
  }

}
