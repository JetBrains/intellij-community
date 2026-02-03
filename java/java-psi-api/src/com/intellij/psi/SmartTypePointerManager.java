// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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

  public abstract @NotNull SmartTypePointer createSmartTypePointer(@NotNull PsiType type);
}