// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  public static SemService getSemService(@NotNull Project p) {
    return ServiceManager.getService(p, SemService.class);
  }

  @Nullable
  public <T extends SemElement> T getSemElement(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    final List<T> list = getSemElements(key, psi);
    if (list.isEmpty()) return null;
    return list.get(0);
  }

  public abstract <T extends SemElement> List<T> getSemElements(SemKey<T> key, @NotNull PsiElement psi);
}
