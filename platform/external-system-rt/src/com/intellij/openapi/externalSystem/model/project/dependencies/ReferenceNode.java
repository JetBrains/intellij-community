// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;

public class ReferenceNode implements DependencyNode, Serializable {
  private final long id;

  @PropertyMapping({"id"})
  public ReferenceNode(long id) {this.id = id;}

  @Override
  public long getId() {
    return id;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return "*";
  }

  @Nullable
  @Override
  public ResolutionState getResolutionState() {
    return null;
  }

  @Nullable
  @Override
  public String getSelectionReason() {
    return null;
  }

  @NotNull
  @Override
  public List<DependencyNode> getDependencies() {
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
    return (int)(id ^ (id >>> 32));
  }
}
