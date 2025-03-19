// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.semantic;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * Register sem providers via extension point {@code com.intellij.semContributor}.
 */
public abstract class SemContributor {
  @ApiStatus.Internal
  public static final ExtensionPointName<SemContributorEP> EP_NAME = new ExtensionPointName<>("com.intellij.semContributor");

  public abstract void registerSemProviders(@NotNull SemRegistrar registrar, @NotNull Project project);

  @ApiStatus.OverrideOnly
  protected boolean isAvailable(@NotNull Project project) {
    return true;
  }

  @ApiStatus.Internal
  public static final class SemContributorHelper {
    public static boolean isAvailable(@NotNull SemContributor contributor, @NotNull Project project) {
      return contributor.isAvailable(project);
    }
  }
}
