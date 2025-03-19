// Copyright 2000-2022 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNameIdentifierOwner;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Provides both line marker and <em>Navigate | Related Symbol</em> targets.
 * <p>
 * Override this class and register the implementation as {@code com.intellij.codeInsight.lineMarkerProvider} extension.
 * </p>
 */
public abstract class RelatedItemLineMarkerProvider extends LineMarkerProviderDescriptor {

  @Override
  public RelatedItemLineMarkerInfo<?> getLineMarkerInfo(@NotNull PsiElement element) {
    return null;
  }

  @Override
  public void collectSlowLineMarkers(@NotNull List<? extends PsiElement> elements,
                                     @NotNull Collection<? super LineMarkerInfo<?>> result) {
    collectNavigationMarkers(elements, result, false);
  }

  public void collectNavigationMarkers(@NotNull List<? extends PsiElement> elements,
                                       @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result,
                                       boolean forNavigation) {
    for (int i = 0, size = elements.size(); i < size; i++) {
      PsiElement element = elements.get(i);
      collectNavigationMarkers(element, result);
      if (forNavigation && element instanceof PsiNameIdentifierOwner) {
        PsiElement nameIdentifier = ((PsiNameIdentifierOwner)element).getNameIdentifier();
        if (nameIdentifier != null && !elements.contains(nameIdentifier)) {
          collectNavigationMarkers(nameIdentifier, result);
        }
      }
    }
  }

  protected void collectNavigationMarkers(@NotNull PsiElement element, @NotNull Collection<? super RelatedItemLineMarkerInfo<?>> result) {
  }

  @Override
  public String getName() {
    return null;
  }
}
