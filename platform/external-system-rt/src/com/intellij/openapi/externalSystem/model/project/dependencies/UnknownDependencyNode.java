// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  @Override
  public @NotNull String getDisplayName() {
    return name == null ? "unknown" : name; //NON-NLS
  }
}
