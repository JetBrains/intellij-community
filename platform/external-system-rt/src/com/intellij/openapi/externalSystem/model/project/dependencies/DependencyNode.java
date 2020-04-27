// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.model.project.dependencies;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public interface DependencyNode {
  long getId();

  @NotNull
  @Nls(capitalization = Nls.Capitalization.Title)
  String getDisplayName();

  @Nullable
  String getResolutionState();

  @NotNull
  List<DependencyNode> getDependencies();
}
