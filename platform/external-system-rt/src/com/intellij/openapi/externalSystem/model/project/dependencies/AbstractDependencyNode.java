// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractDependencyNode implements DependencyNode, Serializable {
  private final long id;
  private final @NotNull List<DependencyNode> dependencies;
  private ResolutionState resolutionState;
  private String selectionReason;

  protected AbstractDependencyNode(long id) {
    this.id = id;
    dependencies = new ArrayList<>(0);
  }

  @Override
  public long getId() {
    return id;
  }

  @Override
  public @NotNull List<DependencyNode> getDependencies() {
    return dependencies;
  }

  @Override
  public @Nullable ResolutionState getResolutionState() {
    return resolutionState;
  }

  public void setResolutionState(@Nullable ResolutionState resolutionState) {
    this.resolutionState = resolutionState;
  }

  @Override
  public @Nullable String getSelectionReason() {
    return selectionReason;
  }

  public void setSelectionReason(@Nullable String selectionReason) {
    this.selectionReason = selectionReason;
  }

  @Override
  public final boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    AbstractDependencyNode node = (AbstractDependencyNode)o;
    return id == node.id;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }
}
