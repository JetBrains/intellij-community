// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DependencyScopeNode extends AbstractDependencyNode {
  private final String scope;
  private final String displayName;
  private final String description;

  @PropertyMapping({"id", "scope", "displayName", "description"}) //NON-NLS
  public DependencyScopeNode(long id, @NotNull String scope, @NotNull String displayName, @Nullable String description) {
    super(id);
    this.displayName = displayName;
    this.scope = scope;
    this.description = description;
  }

  @NotNull
  public String getScope() {
    return scope;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return displayName;
  }

  @Nullable
  public String getDescription() {
    return description;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    DependencyScopeNode node = (DependencyScopeNode)o;
    if (!scope.equals(node.scope)) return false;
    if (!displayName.equals(node.displayName)) return false;
    if (description != null ? !description.equals(node.description) : node.description != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + scope.hashCode();
    result = 31 * result + displayName.hashCode();
    result = 31 * result + (description != null ? description.hashCode() : 0);
    return result;
  }
}
