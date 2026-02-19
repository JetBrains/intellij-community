// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class ReferenceNode implements DependencyNode, Serializable {
  private final long id;

  @PropertyMapping("id")
  public ReferenceNode(long id) {this.id = id;}

  @Override
  public long getId() {
    return id;
  }

  @Override
  public @NotNull String getDisplayName() {
    return "*";
  }

  @Override
  public @Nullable ResolutionState getResolutionState() {
    return null;
  }

  @Override
  public @Nullable String getSelectionReason() {
    return null;
  }

  @Override
  public @NotNull List<DependencyNode> getDependencies() {
    return Collections.emptyList();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ReferenceNode node = (ReferenceNode)o;
    if (id != node.id) return false;
    return true;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(id);
  }
}
