package com.michaelbaranov.microba.common;

import javax.swing.table.AbstractTableModel;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;

/**
 * A simple abstract implementation of <code>BoundedTableModel</code>. A
 * convenience class.
 * 
 * @author Michael Baranov
 */
public abstract class AbstractBoundedTableModel extends AbstractTableModel
    implements BoundedTableModel {
  
  private final PropertyChangeSupport propertySupport = new PropertyChangeSupport(this);

  @Override
  public void addPropertyChangeListener(PropertyChangeListener listener) {
    propertySupport.addPropertyChangeListener(listener);
  }

  @Override
  public void addPropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertySupport.addPropertyChangeListener(propertyName, listener);
  }

  public void firePropertyChange(PropertyChangeEvent evt) {
    propertySupport.firePropertyChange(evt);
  }

  public void firePropertyChange(String propertyName, boolean oldValue, boolean newValue) {
    propertySupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public void firePropertyChange(String propertyName, int oldValue, int newValue) {
    propertySupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public void firePropertyChange(String propertyName, Object oldValue, Object newValue) {
    propertySupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  public PropertyChangeListener[] getPropertyChangeListeners() {
    return propertySupport.getPropertyChangeListeners();
  }

  public PropertyChangeListener[] getPropertyChangeListeners(String propertyName) {
    return propertySupport.getPropertyChangeListeners(propertyName);
  }

  public boolean hasListeners(String propertyName) {
    return propertySupport.hasListeners(propertyName);
  }

  @Override
  public void removePropertyChangeListener(PropertyChangeListener listener) {
    propertySupport.removePropertyChangeListener(listener);
  }

  @Override
  public void removePropertyChangeListener(String propertyName, PropertyChangeListener listener) {
    propertySupport.removePropertyChangeListener(propertyName, listener);
  }
  
}
