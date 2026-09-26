// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;

public interface ProjectNameProvider {
  ExtensionPointName<ProjectNameProvider> EP_NAME = new ExtensionPointName<>("com.intellij.projectNameProvider");

  @ApiStatus.Experimental
  default @Nullable Path getNameFile(@NotNull Project project) { return null; }
}
