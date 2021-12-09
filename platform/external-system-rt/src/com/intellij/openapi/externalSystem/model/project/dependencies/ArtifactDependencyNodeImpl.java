// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  @NotNull
  public String getGroup() {
    return group;
  }

  @Override
  @NotNull
  public String getModule() {
    return module;
  }

  @Override
  @NotNull
  public String getVersion() {
    return version;
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return group + ':' + module + ':' + version; //NON-NLS
  }
}
