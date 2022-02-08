// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Set;

public interface RefactoringConflictsUtil {
  static RefactoringConflictsUtil getInstance() {
    return ApplicationManager.getApplication().getService(RefactoringConflictsUtil.class);
  }

  /**
   * Analyzes accessibility after members move to other place (for example class) and contributes to the conflicts map if needed.
   * Ensures that all references to members will be able to resolve after move.
   *  @param targetClass   class to which all members will be moved
   * @param newVisibility visibility of all members after move
   * @param membersToMove set of members to be moved
   * @param conflicts     map of conflicts to which this method will contribute
   */
  @Contract(mutates = "param4")
  void analyzeAccessibilityConflictsAfterMemberMove(@NotNull PsiClass targetClass,
                                                    @Nullable @PsiModifier.ModifierConstant String newVisibility,
                                                    @NotNull Set<? extends PsiMember> membersToMove,
                                                    @NotNull MultiMap<PsiElement, String> conflicts);

  /**
   * Analyzes accessibility after members move to other place (for example class) and contributes to the conflicts map if needed.
   * Ensures that members will be accessible for all references to members in new place.
   *
   * @param membersToMove   set of members to be moved
   * @param targetClass     class to which all members will be moved
   * @param newVisibility   visibility of all members after move
   * @param context         place where members are expected to be moved (maybe useful if targetClass is null)
   * @param abstractMethods methods to keep abstract in new place
   * @param ignorePredicate whether we need to check accessibility of a particular reference to a member
   * @param conflicts       map of conflicts to which this method will contribute
   */
  @Contract(mutates = "param7")
  void analyzeAccessibilityConflictsAfterMemberMove(@NotNull Set<? extends PsiMember> membersToMove,
                                                    @Nullable PsiClass targetClass,
                                                    @Nullable String newVisibility,
                                                    @NotNull @PsiModifier.ModifierConstant PsiElement context,
                                                    @Nullable Set<? extends PsiMethod> abstractMethods,
                                                    @NotNull Condition<? super PsiReference> ignorePredicate,
                                                    @NotNull MultiMap<PsiElement, String> conflicts);

  /**
   * Ensures that inheritor overloads will be able to access this method after change of visibility modifier as well as super methods will
   * be compatible with new modifier. Conflicts will be written to the conflict map.
   *
   * @param method    method which changes its visibility modifier
   * @param modifier  new visibility modifier of the method
   * @param conflicts map of conflicts to which this method will contribute
   */
  @Contract(mutates = "param3")
  void analyzeHierarchyConflictsAfterMethodModifierChange(@NotNull PsiMethod method,
                                                          @NotNull @PsiModifier.ModifierConstant String modifier,
                                                          @NotNull MultiMap<PsiElement, @Nls String> conflicts);

  /**
   * Searches for conflicts appearing because of move of elements from one module to another.
   * For example, elements require dependency on module absent in target module. Conflicts will be written to the conflict map.
   *
   * @param scopes    places to search references in (elements to move)
   * @param vFile     target file or directory
   * @param conflicts map of conflicts to which this method will contribute
   */
  @Contract(mutates = "param5")
  void analyzeModuleConflicts(@NotNull Project project,
                              @Nullable Collection<? extends PsiElement> scopes,
                              UsageInfo[] usages,
                              @NotNull VirtualFile vFile,
                              @NotNull MultiMap<PsiElement, @Nls String> conflicts);

  /**
   * Analyses conflicts appearing after deletion of a given parameter (for example, method will have the same erasure as existing one).
   *
   * @param method    method in which parameter will be deleted
   * @param parameter parameter to delete
   * @param conflicts map of conflicts to which this method will contribute
   */
  @Contract(mutates = "param3")
  void analyzeMethodConflictsAfterParameterDelete(@NotNull PsiMethod method,
                                                  @NotNull PsiParameter parameter,
                                                  @NotNull MultiMap<PsiElement, @Nls String> conflicts);
}
