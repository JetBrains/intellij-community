package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 2/14/12 1:32 PM
 */
public abstract class AbstractExternalEntityId implements ProjectEntityId {

  @NotNull private final AtomicReference<ProjectSystemId> myOwner = new AtomicReference<ProjectSystemId>();
  @NotNull private final ProjectEntityType myType;

  public AbstractExternalEntityId(@NotNull ProjectEntityType type, @NotNull ProjectSystemId owner) {
    myType = type;
    myOwner.set(owner);
  }

  @Override
  @NotNull
  public ProjectEntityType getType() {
    return myType;
  }

  @Override
  @NotNull
  public ProjectSystemId getOwner() {
    return myOwner.get();
  }

  @Override
  public void setOwner(@NotNull ProjectSystemId owner) {
    myOwner.set(owner);
  }

  @Override
  public int hashCode() {
    return myType.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractExternalEntityId that = (AbstractExternalEntityId)o;
    return myType.equals(that.myType);
  }
}
