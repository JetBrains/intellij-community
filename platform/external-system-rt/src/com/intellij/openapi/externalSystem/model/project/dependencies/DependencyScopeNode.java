// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public String getScope() {
    return scope;
  }

  @NotNull
  @Override
  public @Nls String getDisplayName() {
    return displayName;
  }

  @Nullable
  public @Nls String getDescription() {
    return description;
  }

  @Override
  public boolean match(AbstractDependencyNode dependencyNode) {
    if (dependencyNode == null || getClass() != dependencyNode.getClass()) return false;
    DependencyScopeNode node = (DependencyScopeNode)dependencyNode;
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
