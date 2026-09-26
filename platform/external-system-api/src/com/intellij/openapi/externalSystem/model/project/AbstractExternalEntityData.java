// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractExternalEntityData implements ExternalEntityData {
  private final @NotNull ProjectSystemId owner;

  public AbstractExternalEntityData(@NotNull ProjectSystemId owner) {
    this.owner = owner;
  }

  @Override
  public @NotNull ProjectSystemId getOwner() {
    return owner;
  }

  @Override
  public int hashCode() {
    return owner.hashCode();
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
    return owner.equals(that.owner);
  }
}