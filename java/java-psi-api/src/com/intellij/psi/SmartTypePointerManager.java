// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public abstract class SmartTypePointerManager {
  public static SmartTypePointerManager getInstance(Project project) {
    return project.getService(SmartTypePointerManager.class);
  }

  @NotNull
  public abstract SmartTypePointer createSmartTypePointer(@NotNull PsiType type);
}