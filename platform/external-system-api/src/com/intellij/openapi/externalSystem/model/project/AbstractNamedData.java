package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/25/11 5:38 PM
 */
public abstract class AbstractNamedData extends AbstractExternalEntityData implements Named {

  private static final long serialVersionUID = 1L;
  
  private String myName;

  public AbstractNamedData(@NotNull ProjectSystemId owner, @NotNull String name) {
    super(owner);
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractNamedData that = (AbstractNamedData)o;
    return myName.equals(that.myName);
  }
}
