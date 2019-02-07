// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Register sem providers via extension point {@code com.intellij.semContributor}.
 *
 * @author peter
 */
public abstract class SemContributor {
  public static final ProjectExtensionPointName<SemContributorEP> EP_NAME = new ProjectExtensionPointName<>("com.intellij.semContributor");

  public abstract void registerSemProviders(@NotNull SemRegistrar registrar);
}
