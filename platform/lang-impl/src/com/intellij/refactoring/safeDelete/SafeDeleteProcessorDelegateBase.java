// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.openapi.module.Module;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.usages.UsageView;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.UsageViewPresentation;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

import static com.intellij.openapi.util.NlsContexts.DialogMessage;

public abstract class SafeDeleteProcessorDelegateBase implements SafeDeleteProcessorDelegate {
  public abstract @Nullable Collection<? extends PsiElement> getElementsToSearch(
    @NotNull PsiElement element, @Nullable Module module, @NotNull Collection<? extends PsiElement> allElementsToDelete);

  @Override
  public Collection<? extends PsiElement> getElementsToSearch(@NotNull PsiElement element,
                                                              @NotNull Collection<? extends PsiElement> allElementsToDelete) {
    return getElementsToSearch(element, null, allElementsToDelete);
  }

  public @Nullable UsageView showUsages(UsageInfo @NotNull [] usages,
                                        @NotNull UsageViewPresentation presentation,
                                        @NotNull UsageViewManager manager,
                                        PsiElement @NotNull [] elements) {
    return null;
  }

  /**
   * @deprecated Override {@link SafeDeleteProcessorDelegate#findConflicts(PsiElement, PsiElement[], UsageInfo[], MultiMap)} instead
   */
  @Deprecated
  public @Nullable Collection<@DialogMessage String> findConflicts(@NotNull PsiElement element,
                                                                   PsiElement @NotNull [] elements,
                                                                   UsageInfo @NotNull [] usages) {
    return findConflicts(element, elements);
  }
}
