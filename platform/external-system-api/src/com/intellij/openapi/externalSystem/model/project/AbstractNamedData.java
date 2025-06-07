// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractNamedData extends AbstractExternalEntityData implements Named {
  private @NotNull String externalName;
  private @NotNull String internalName;

  public AbstractNamedData(@NotNull ProjectSystemId owner, @NotNull String externalName) {
    this(owner, externalName, externalName);
  }

  public AbstractNamedData(@NotNull ProjectSystemId owner, @NotNull String externalName, @NotNull String internalName) {
    super(owner);
    this.externalName = externalName;
    this.internalName = internalName;
  }

  @Override
  public @NotNull @NlsSafe String getExternalName() {
    return externalName;
  }

  @Override
  public void setExternalName(@NotNull String name) {
    externalName = name;
  }

  @Override
  public @NotNull String getInternalName() {
    return internalName;
  }

  @Override
  public void setInternalName(@NotNull String name) {
    internalName = name;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + externalName.hashCode();
    result = 31 * result + internalName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    AbstractNamedData data = (AbstractNamedData)o;

    if (!externalName.equals(data.externalName)) return false;
    if (!internalName.equals(data.internalName)) return false;
    return true;
  }
}
