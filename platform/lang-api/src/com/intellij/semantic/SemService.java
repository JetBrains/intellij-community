// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.semantic;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValuesManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Provides a way to link some additional data to a psi element.
 *
 * The API is very close to {@link CachedValuesManager#getCachedValue} but the main difference is that
 * a provider isn't passed to the method, but registered separately via {{@link SemContributor}}.
 * Any key can have more than one provider.
 * One more difference with {@link CachedValuesManager} is that a key can be extended by other keys.
 * Cached value will be dropped automatically on any change of PSI.
 *
 * @author peter
 * @see SemElement
 * @see SemContributor
 */
public abstract class SemService {
  public static SemService getSemService(@NotNull Project p) {
    return p.getService(SemService.class);
  }

  @Nullable
  public <T extends SemElement> T getSemElement(@NotNull SemKey<T> key, @NotNull PsiElement psi) {
    final List<T> list = getSemElements(key, psi);
    if (list.isEmpty()) return null;
    return list.get(0);
  }

  @NotNull
  public abstract <T extends SemElement> List<T> getSemElements(SemKey<T> key, @NotNull PsiElement psi);
}
