package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;

/**
 * @author Denis Zhdanov
 * @since 8/25/11 3:44 PM
 */
public abstract class AbstractProjectEntityData implements ProjectEntityData {

  private static final long serialVersionUID = 1L;
  
  @NotNull private ProjectSystemId myOwner;
  
  private transient PropertyChangeSupport myPropertyChangeSupport;

  public AbstractProjectEntityData(@NotNull ProjectSystemId owner) {
    myOwner = owner;
    myPropertyChangeSupport = new PropertyChangeSupport(this);
  }

  @Override
  @NotNull
  public ProjectSystemId getOwner() {
    return myOwner;
  }

  @Override
  public void addPropertyChangeListener(@NotNull PropertyChangeListener listener) {
    myPropertyChangeSupport.addPropertyChangeListener(listener);
  }
  
  protected void firePropertyChange(@NotNull String propertyName, @NotNull Object oldValue, @NotNull Object newValue) {
    myPropertyChangeSupport.firePropertyChange(propertyName, oldValue, newValue);
  }

  private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
    myPropertyChangeSupport = new PropertyChangeSupport(this);
    myOwner = (ProjectSystemId)in.readObject();
  }
  
  @Override
  public int hashCode() {
    return myOwner.hashCode();
  }

  @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AbstractProjectEntityData that = (AbstractProjectEntityData)obj;
    return myOwner.equals(that.myOwner);
  }
} 