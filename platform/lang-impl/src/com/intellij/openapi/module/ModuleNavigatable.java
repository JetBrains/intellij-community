// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.module;

import com.intellij.openapi.roots.ui.configuration.ProjectSettingsService;
import com.intellij.pom.Navigatable;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public final class ModuleNavigatable implements Navigatable {
  private final Module module;

  public ModuleNavigatable(@NotNull Module module) {
    this.module = module;
  }

  @Override
  public void navigate(boolean requestFocus) {
    ProjectSettingsService.getInstance(module.getProject()).openContentEntriesSettings(module);
  }

  @Override
  public boolean canNavigate() {
    return !module.isDisposed();
  }
}
