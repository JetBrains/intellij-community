// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public String getPath() {
    return path;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return displayName;
  }
}
