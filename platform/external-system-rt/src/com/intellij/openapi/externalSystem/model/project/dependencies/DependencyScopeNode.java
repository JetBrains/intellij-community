// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependencyScopeNode extends AbstractDependencyNode {
  private final String scope;
  private final @Nls String displayName;
  private final @Nls String description;

  @PropertyMapping({"id", "scope", "displayName", "description"})
  public DependencyScopeNode(long id, @NotNull String scope, @NotNull String displayName, @Nullable String description) {
    super(id);
    this.displayName = displayName; //NON-NLS
    this.scope = scope;
    this.description = description; //NON-NLS
  }

  public @NotNull String getScope() {
    return scope;
  }

  @Override
  public @NotNull @Nls String getDisplayName() {
    return displayName;
  }

  public @Nullable @Nls String getDescription() {
    return description;
  }
}
