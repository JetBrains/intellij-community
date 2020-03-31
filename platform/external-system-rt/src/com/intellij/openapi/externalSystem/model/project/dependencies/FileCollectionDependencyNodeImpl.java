// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

public class FileCollectionDependencyNodeImpl extends AbstractDependencyNode implements FileCollectionDependencyNode {

  private final String displayName;
  private final String path;

  @PropertyMapping({"id", "displayName", "path"}) //NON-NLS
  public FileCollectionDependencyNodeImpl(long id, @NotNull String displayName, @NotNull String path) {
    super(id);
    this.displayName = displayName;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    FileCollectionDependencyNodeImpl node = (FileCollectionDependencyNodeImpl)o;
    if (!displayName.equals(node.displayName)) return false;
    if (!path.equals(node.path)) return false;
    return true;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + displayName.hashCode();
    result = 31 * result + path.hashCode();
    return result;
  }
}
