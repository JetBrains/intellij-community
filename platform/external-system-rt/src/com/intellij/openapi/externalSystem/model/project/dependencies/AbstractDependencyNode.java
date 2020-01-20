// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import java.io.Serializable;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class AbstractDependencyNode implements DependencyNode, Serializable {
  private final long id;
  private final Set<DependencyNode> dependencies = new LinkedHashSet<DependencyNode>();
  private String resolutionState;

  protected AbstractDependencyNode(long id) {this.id = id;}

  @Override
  public long getId() {
    return id;
  }

  @Override
  public Set<DependencyNode> getDependencies() {
    return dependencies;
  }

  @Override
  public String getResolutionState() {
    return resolutionState;
  }

  public void setResolutionState(String resolutionState) {
    this.resolutionState = resolutionState;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractDependencyNode node = (AbstractDependencyNode)o;
    if (id != node.id) return false;
    if (!dependencies.equals(node.dependencies)) return false;
    if (resolutionState != null ? !resolutionState.equals(node.resolutionState) : node.resolutionState != null) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = (int)(id ^ (id >>> 32));
    result = 31 * result + dependencies.hashCode();
    result = 31 * result + (resolutionState != null ? resolutionState.hashCode() : 0);
    return result;
  }
}
