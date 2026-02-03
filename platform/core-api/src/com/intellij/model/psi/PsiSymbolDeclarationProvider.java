// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.model.psi;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

/**
 * Register the implementation of this interface at {@code com.intellij.psi.declarationProvider} extension point
 * to provide symbol declarations by PsiElement.
 * <p>
 * TODO: consider more declarative API similar to {@link PsiSymbolReferenceProviderBean}.
 *
 * @see PsiSymbolDeclaration
 */
public interface PsiSymbolDeclarationProvider {

  /**
   * This method is used to determine whether there are any declarations at the caret position,
   * e.g., when invoking Find Usages inside some file.
   * <p/>
   * The method is invoked for each PsiElement starting from the bottom PsiElement (leaf)
   * and traversing tree up to the containing PsiFile,
   * which means there is no need to traverse the tree inside the implementation.
   * The offset is adjusted at each level to be relative to the element on the current level.
   * <p/>
   * The method is invoked in read action.
   * The contents of the returned collection are copied after the method returns,
   * the platform doesn't store or modify the returned collection.
   *
   * @param element         PsiElement in the code which might represent some declaration
   * @param offsetInElement offset relative to the {@code element}, which should be inside
   *                        {@linkplain PsiSymbolDeclaration#getRangeInDeclaringElement ranges of returned declarations},
   *                        or {@code -1} if all declarations are requested.
   *                        The offset serves as a hint to avoid computing declarations, which cannot contain the offset.
   *                        The platform filters returned declarations by the offset later
   * @see com.intellij.psi.util.PsiTreeUtilKt#elementsAroundOffsetUp(com.intellij.psi.PsiFile, int)
   */
  @NotNull @Unmodifiable
  Collection<? extends @NotNull PsiSymbolDeclaration> getDeclarations(@NotNull PsiElement element, int offsetInElement);
}
