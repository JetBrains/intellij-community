// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Register sem providers via extension point {@code com.intellij.semContributor}.
 *
 * @author peter
 */
public abstract class SemContributor {
  public static final ExtensionPointName<SemContributorEP> EP_NAME = new ExtensionPointName<>("com.intellij.semContributor");

  /**
   * @deprecated Use {@link #registerSemProviders(SemRegistrar, Project)}
   */
  @Deprecated
  public void registerSemProviders(@NotNull SemRegistrar registrar) {
    throw new AbstractMethodError();
  }

  public void registerSemProviders(@NotNull SemRegistrar registrar, @NotNull Project project) {
    //noinspection deprecation
    registerSemProviders(registrar);
  }
}
