// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import org.jetbrains.annotations.NotNull;

public interface ArtifactDependencyNode extends DependencyNode {
  @NotNull
  String getGroup();

  @NotNull
  String getModule();

  @NotNull
  String getVersion();
}
