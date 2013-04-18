package com.intellij.openapi.externalSystem.model.project.id;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.ProjectEntityType;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 2/20/12 12:01 PM
 */
public abstract  class AbstractExternalDependencyId extends AbstractExternalEntityId {

  @NotNull private final String myOwnerModuleName;
  @NotNull private final String myDependencyName;
  
  public AbstractExternalDependencyId(@NotNull ProjectEntityType type,
                                      @NotNull ProjectSystemId owner,
                                      @NotNull String ownerModuleName,
                                      @NotNull String dependencyName)
  {
    super(type, owner);
    myOwnerModuleName = ownerModuleName;
    myDependencyName = dependencyName;
  }

  @NotNull
  public String getOwnerModuleName() {
    return myOwnerModuleName;
  }

  @NotNull
  public String getDependencyName() {
    return myDependencyName;
  }

  @NotNull
  public ModuleId getOwnerModuleId() {
    return new ModuleId(getOwner(), myOwnerModuleName);
  }
  
  @Override
  public int hashCode() {
    int result = 31 * super.hashCode() + myOwnerModuleName.hashCode();
    return 31 * result + myDependencyName.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) {
      return false;
    }
    AbstractExternalDependencyId that = (AbstractExternalDependencyId)o;
    return myOwnerModuleName.equals(that.myOwnerModuleName) && myDependencyName.equals(that.myDependencyName);
  }
}
