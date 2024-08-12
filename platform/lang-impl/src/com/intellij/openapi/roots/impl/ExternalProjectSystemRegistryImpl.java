// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ExternalProjectSystemRegistry;
import com.intellij.openapi.roots.ProjectModelExternalSource;
import org.jetbrains.annotations.NotNull;

public final class ExternalProjectSystemRegistryImpl implements ExternalProjectSystemRegistry {
  @Override
  public ProjectModelExternalSource getExternalSource(@NotNull Module module) {
    return null;
  }

  @Override
  public @NotNull ProjectModelExternalSource getSourceById(@NotNull String id) {
    throw new IllegalStateException();
  }
}
