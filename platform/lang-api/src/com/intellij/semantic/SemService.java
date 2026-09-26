// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.semantic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides a way to link some additional data to a psi element.
 * <p>
 * The API is very close to {@link CachedValuesManager#getCachedValue} but the main difference is that
 * a provider isn't passed to the method, but registered separately via {{@link SemContributor}}.
 * Any key can have more than one provider.
 * One more difference with {@link CachedValuesManager} is that a key can be extended by other keys.
 * Cached value will be dropped automatically on any change of PSI.
 *
 * @see SemElement
 * @see SemContributor
 */
public abstract class SemService {
  public static SemService getSemService(@NotNull Project project) {
    return project.getService(SemService.class);
  }

  public @Nullable <T extends SemElement> T getSemElement(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    List<T> list = getSemElements(key, psi);
    return list.isEmpty() ? null : list.get(0);
  }

  public abstract @NotNull <T extends SemElement> List<T> getSemElements(SemKey<T> key, @NotNull PsiElement psi);

  /**
   * Creates and returns SEM elements but does not cache them in the PSI if it does not have SEM cache already assigned.
   */
  public abstract @NotNull <T extends SemElement> List<T> getSemElementsNoCache(SemKey<T> key, @NotNull PsiElement psi);
}
