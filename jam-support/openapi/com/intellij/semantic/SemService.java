/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.semantic;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author peter
 */
public abstract class SemService {

  public static SemService getSemService(Project p) {
    return ServiceManager.getService(p, SemService.class);
  }

  @Nullable
  public <T extends SemElement> T getSemElement(SemKey<T> key, @NotNull PsiElement psi) {
    final List<T> list = getSemElements(key, psi);
    if (list.isEmpty()) return null;
    return list.get(0);
  }

  public abstract <T extends SemElement> List<T> getSemElements(SemKey<T> key, @NotNull PsiElement psi);

  @Nullable
  public abstract <T extends SemElement> List<T> getCachedSemElements(SemKey<T> key, @NotNull PsiElement psi);

  public abstract <T extends SemElement> void setCachedSemElement(SemKey<T> key, @NotNull PsiElement psi, @Nullable T semElement);

  public abstract void clearCache();

  /**
   * Caches won't be cleared on PSI changes inside this action
   * @param change the action
   */
  public abstract void performAtomicChange(@NotNull Runnable change);

  public abstract boolean isInsideAtomicChange();
}
