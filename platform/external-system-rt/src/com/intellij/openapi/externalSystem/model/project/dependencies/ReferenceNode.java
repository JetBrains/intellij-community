// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serializable;
import java.util.Collections;
import java.util.Set;

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
  public String getResolutionState() {
    return null;
  }

  @Override
  public Set<DependencyNode> getDependencies() {
    return Collections.emptySet();
  }
}
