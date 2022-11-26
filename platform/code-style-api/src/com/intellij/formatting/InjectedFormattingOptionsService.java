// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

/**
 * Determines formatting behavior for injected PSI files
 */
public interface InjectedFormattingOptionsService {

  static InjectedFormattingOptionsService getInstance() {
    return ApplicationManager.getApplication().getService(InjectedFormattingOptionsService.class);
  }

  /**
   * For a given PSI file, returns
   * - `true` if code formatting should be delegated to a file which contains this injected file (top-level file)
   * - `false` if code formatting shouldn't be delegated to a top-level file
   */
  boolean shouldDelegateToTopLevel(@NotNull PsiFile file);
}
