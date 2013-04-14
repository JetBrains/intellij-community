package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.id.ProjectEntityId;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.Serializable;

/**
 * Stands for an entity from external system domain. An external system might be 'maven', 'gradle', 'eclipse' etc, i.e. any
 * framework or platform which defines project structure model in its own terms. That module might be represented in terms
 * of this interface implementations then.
 * <p/>
 * It's assumed to be safe to use implementations of this interface at hash-based containers (i.e. they are expected to correctly 
 * override {@link #equals(Object)} and {@link #hashCode()}.
 * 
 * @author Denis Zhdanov
 * @since 8/12/11 12:50 PM
 */
public interface ProjectEntityData extends Serializable {

  @NotNull
  ProjectSystemId getOwner();

  /**
   * Allows to map current data to its id.
   * 
   * @param dataNode  data holder which holds current data (if any)
   * @return            current data id
   */
  @NotNull
  ProjectEntityId getId(@Nullable DataNode<?> dataNode);
  
  /**
   * Follows contract of {@link PropertyChangeSupport#addPropertyChangeListener(PropertyChangeListener)}  
   * 
   * @param listener  target listener
   */
  void addPropertyChangeListener(@NotNull PropertyChangeListener listener);
}
