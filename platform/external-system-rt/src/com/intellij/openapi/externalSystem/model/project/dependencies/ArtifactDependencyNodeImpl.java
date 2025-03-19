// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import com.intellij.serialization.PropertyMapping;
import org.jetbrains.annotations.NotNull;

public class ArtifactDependencyNodeImpl extends AbstractDependencyNode implements ArtifactDependencyNode {

  private final String group;
  private final String module;
  private final String version;

  @PropertyMapping({"id", "group", "module", "version"})
  public ArtifactDependencyNodeImpl(long id, @NotNull String group, @NotNull String module, @NotNull String version) {
    super(id);
    this.group = group;
    this.module = module;
    this.version = version;
  }

  @Override
  public @NotNull String getGroup() {
    return group;
  }

  @Override
  public @NotNull String getModule() {
    return module;
  }

  @Override
  public @NotNull String getVersion() {
    return version;
  }

  @Override
  public @NotNull String getDisplayName() {
    return group + ':' + module + ':' + version; //NON-NLS
  }
}
