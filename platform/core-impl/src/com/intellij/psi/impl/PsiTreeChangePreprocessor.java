// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ProjectExtensionPointName;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

/**
 * An extension called before notifying {@link com.intellij.psi.PsiTreeChangeListener}s of events.<p></p>
 * <p>
 * Try to avoid processing PSI events at all cost! See {@link com.intellij.psi.PsiTreeChangeEvent} documentation for more details.
 */
public interface PsiTreeChangePreprocessor {
  ProjectExtensionPointName<PsiTreeChangePreprocessor> EP = new ProjectExtensionPointName<>("com.intellij.psi.treeChangePreprocessor");

  /**
   * @deprecated Use {@link #EP}
   */
  @Deprecated
  @ApiStatus.ScheduledForRemoval  
  ExtensionPointName<PsiTreeChangePreprocessor> EP_NAME = ExtensionPointName.create("com.intellij.psi.treeChangePreprocessor");

  void treeChanged(@NotNull PsiTreeChangeEventImpl event);
}
