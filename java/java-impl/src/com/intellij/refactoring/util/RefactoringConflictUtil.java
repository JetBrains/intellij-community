// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

@Service
public interface RefactoringConflictUtil {
  static RefactoringConflictUtil getInstance() {
    return ApplicationManager.getApplication().getService(RefactoringConflictUtil.class);
  }

  void analyzeAccessibilityConflictsAfterMemberMove(@NotNull Set<? extends PsiMember> membersToMove,
                                                    @NotNull PsiClass targetClass,
                                                    @NotNull MultiMap<PsiElement, String> conflicts,
                                                    @Nullable String newVisibility);

  void analyzeAccessibilityConflictsAfterMemberMove(@NotNull Set<? extends PsiMember> membersToMove,
                                                    @Nullable PsiClass targetClass,
                                                    @NotNull MultiMap<PsiElement, String> conflicts,
                                                    @Nullable String newVisibility,
                                                    @NotNull PsiElement context,
                                                    @Nullable Set<? extends PsiMethod> abstractMethods,
                                                    @NotNull Condition<PsiReference> ignorePredicate);

  void searchForHierarchyConflicts(PsiMethod methodToChange, MultiMap<PsiElement, @Nls String> conflicts, final String modifier);

  void analyzeModuleConflicts(final Project project,
                              final Collection<? extends PsiElement> scopes,
                              final UsageInfo[] usages,
                              final VirtualFile vFile,
                              final MultiMap<PsiElement, String> conflicts);

  void collectMethodConflictsForDeletion(MultiMap<PsiElement, String> conflicts, PsiMethod method, PsiParameter parameter);

  void checkAccessibilityConflictsAfterMove(@NotNull PsiMember member,
                                            @PsiModifier.ModifierConstant @Nullable String newVisibility,
                                            @Nullable PsiClass targetClass,
                                            @NotNull Set<? extends PsiMember> membersToMove,
                                            @NotNull MultiMap<PsiElement, String> conflicts,
                                            @NotNull Condition<PsiReference> ignorePredicate);

  void checkAccessibilityConflictsAfterMove(@NotNull PsiReference reference,
                                            @NotNull PsiMember member,
                                            @Nullable PsiModifierList modifierListCopy,
                                            @Nullable PsiClass targetClass,
                                            @NotNull Set<? extends PsiMember> membersToMove,
                                            @NotNull MultiMap<PsiElement, String> conflicts);

  void checkUsedElementsAfterMove(PsiMember member,
                                  PsiElement scope,
                                  @NotNull Set<? extends PsiMember> membersToMove,
                                  @Nullable Set<? extends PsiMethod> abstractMethods,
                                  @Nullable PsiClass targetClass,
                                  @NotNull PsiElement context,
                                  MultiMap<PsiElement, String> conflicts);

  void checkAccessibilityAfterMove(PsiMember refMember,
                                   @NotNull PsiElement newContext,
                                   @Nullable PsiClass accessClass,
                                   PsiMember member,
                                   MultiMap<PsiElement, String> conflicts);
}
