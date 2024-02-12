// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.actionSystem.ActionUpdateThreadAware;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.PossiblyDumbAware;
import org.jetbrains.annotations.NotNull;

/**
 * During indexing, only the providers implementing {@link com.intellij.openapi.project.DumbAware} are queried.
 * @see com.intellij.openapi.project.DumbService
 */
public interface CutProvider extends ActionUpdateThreadAware, PossiblyDumbAware {
  void performCut(@NotNull DataContext dataContext);
  boolean isCutEnabled(@NotNull DataContext dataContext);
  boolean isCutVisible(@NotNull DataContext dataContext);
}
