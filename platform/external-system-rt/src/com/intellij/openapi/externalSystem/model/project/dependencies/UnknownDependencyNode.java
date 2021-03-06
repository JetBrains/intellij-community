// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class UnknownDependencyNode extends AbstractDependencyNode {
  private final String name;

  @PropertyMapping({"id", "name"})
  public UnknownDependencyNode(long id, @Nls String name) {
    super(id);
    this.name = name;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return name == null ? "unknown" : name; //NON-NLS
  }

  @Override
  public boolean match(AbstractDependencyNode dependencyNode) {
    if (dependencyNode == null || getClass() != dependencyNode.getClass()) return false;
    UnknownDependencyNode node = (UnknownDependencyNode)dependencyNode;
    if (name != null ? !name.equals(node.name) : node.name != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (name != null ? name.hashCode() : 0);
    return result;
  }
}
