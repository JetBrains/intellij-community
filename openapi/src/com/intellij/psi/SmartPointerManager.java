/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.psi;

import com.intellij.openapi.project.Project;

public abstract class SmartPointerManager {
  public static SmartPointerManager getInstance(Project project) {
    return project.getComponent(SmartPointerManager.class);
  }

  public abstract SmartPsiElementPointer createSmartPsiElementPointer(PsiElement element);
  public abstract SmartTypePointer createSmartTypePointer(PsiType type);
  public abstract SmartPsiElementPointer createLazyPointer(PsiElement element);
}
