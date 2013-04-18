package com.intellij.openapi.externalSystem.model.project;

import org.jetbrains.annotations.NotNull;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Comparator;

/**
 * @author Denis Zhdanov
 * @since 8/12/11 12:34 PM
 */
public interface Named {

  /** Key of the {@link #getName() name} property to use with {@link PropertyChangeListener#propertyChange(PropertyChangeEvent)}. */
  String NAME_PROPERTY = "Name";
  
  Comparator<Named> COMPARATOR = new Comparator<Named>() {
    @Override
    public int compare(Named o1, Named o2) {
      return o1.getName().compareTo(o2.getName());
    }
  };
  
  @NotNull
  String getName();

  void setName(@NotNull String name);
}
