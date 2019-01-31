package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 */
public abstract class AbstractExternalEntityData implements ExternalEntityData {

  private static final long serialVersionUID = 1L;
  
  @NotNull private final ProjectSystemId myOwner;
  
  public AbstractExternalEntityData(@NotNull ProjectSystemId owner) {
    myOwner = owner;
  }

  @Override
  @NotNull
  public ProjectSystemId getOwner() {
    return myOwner;
  }

  @Override
  public int hashCode() {
    return myOwner.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    AbstractExternalEntityData that = (AbstractExternalEntityData)obj;
    return myOwner.equals(that.myOwner);
  }
} 