// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project;

import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import org.jetbrains.annotations.NotNull;

public abstract class AbstractExternalEntityData implements ExternalEntityData {
  @NotNull
  private final ProjectSystemId owner;

  public AbstractExternalEntityData(@NotNull ProjectSystemId owner) {
    this.owner = owner;
  }

  @Override
  @NotNull
  public ProjectSystemId getOwner() {
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