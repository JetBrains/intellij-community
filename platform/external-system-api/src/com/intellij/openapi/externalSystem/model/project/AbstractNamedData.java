package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

/**
 * @author Denis Zhdanov
 * @since 8/25/11 5:38 PM
 */
public abstract class AbstractNamedData extends AbstractExternalEntityData implements Named {

  private static final long serialVersionUID = 1L;

  @NotNull
  private String myExternalName;
  @NotNull
  private String myInternalName;

  public AbstractNamedData(@NotNull ProjectSystemId owner, @NotNull String externalName) {
    this(owner, externalName, externalName);
  }

  public AbstractNamedData(@NotNull ProjectSystemId owner, @NotNull String externalName, @NotNull String internalName) {
    super(owner);
    myExternalName = externalName;
    myInternalName = internalName;
  }

  /**
   * please use {@link #getExternalName()} or {@link #getInternalName()} instead
   */
  @NotNull
  @Deprecated
  @Override
  public String getName() {
    return getExternalName();
  }

  /**
   * please use {@link #setExternalName(String)} or {@link #setInternalName(String)} instead
   */
  @Deprecated
  @Override
  public void setName(@NotNull String name) {
    setExternalName(name);
  }

  @NotNull
  @Override
  public String getExternalName() {
    return myExternalName;
  }

  @Override
  public void setExternalName(@NotNull String name) {
    myExternalName = name;
  }

  @NotNull
  @Override
  public String getInternalName() {
    return myInternalName;
  }

  @Override
  public void setInternalName(@NotNull String name) {
    myInternalName = name;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myExternalName.hashCode();
    result = 31 * result + myInternalName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractNamedData data = (AbstractNamedData)o;

    if (!myExternalName.equals(data.myExternalName)) return false;
    if (!myInternalName.equals(data.myInternalName)) return false;
    return true;
  }
}
