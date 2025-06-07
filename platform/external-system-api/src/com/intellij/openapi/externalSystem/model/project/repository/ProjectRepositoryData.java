// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.repository;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ProjectRepositoryData extends AbstractExternalEntityData {

  public static final Key<ProjectRepositoryData> KEY = Key.create(
    ProjectRepositoryData.class,
    ExternalSystemConstants.BUILTIN_LIBRARY_DATA_SERVICE_ORDER
  );

  private final @NotNull String name;

  @PropertyMapping({"owner", "name"})
  public ProjectRepositoryData(@NotNull ProjectSystemId owner, @NotNull String name) {
    super(owner);
    this.name = name;
  }

  public @NotNull String getName() {
    return name;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    ProjectRepositoryData data = (ProjectRepositoryData)o;
    return Objects.equals(name, data.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), name);
  }
}
