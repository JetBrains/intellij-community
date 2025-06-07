// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public class FileCollectionDependencyNodeImpl extends AbstractDependencyNode implements FileCollectionDependencyNode {

  private final @Nls String displayName;
  private final String path;

  @PropertyMapping({"id", "displayName", "path"})
  public FileCollectionDependencyNodeImpl(long id, @NotNull String displayName, @NotNull String path) {
    super(id);
    this.displayName = displayName; //NON-NLS
    this.path = path;
  }

  @Override
  public @NotNull String getPath() {
    return path;
  }

  @Override
  public @NotNull String getDisplayName() {
    return displayName;
  }
}
