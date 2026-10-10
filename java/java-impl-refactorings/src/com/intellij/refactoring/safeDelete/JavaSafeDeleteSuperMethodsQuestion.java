// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.ide.util.SuperMethodWarningUtil;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * The question of the interactive safe delete of a method that overrides some: whether to delete the base methods too. It shows the
 * dialog of the legacy flow, and the answer is the name of the method in {@link ModPlanJavaSafeDeleteOptions#SUPER_METHODS_OF}, chosen for
 * a yes and not chosen for a no. A method is asked about on its own, so the answer of one method does not change the answer of another.
 * {@link ModPlanJavaSafeDeleteOptions#DELETE_SUPER_METHODS} is the general answer of a request, and the dialog does not set it.
 */
final class JavaSafeDeleteSuperMethodsQuestion implements ModPlanQuestion.Custom {
  private final @NotNull SymbolPointer myMethod;
  private final @Nullable String myMethodName;

  JavaSafeDeleteSuperMethodsQuestion(@NotNull PsiMethod method) {
    myMethod = SymbolPointer.of(method);
    myMethodName = JavaSafeDeleteProcessor.externalNameOf(method);
  }

  /**
   * @param targets all the elements that are deleted: a base method that is deleted anyway is not asked about
   */
  static boolean hasSuperMethods(@NotNull PsiMethod method, @NotNull Collection<? extends PsiElement> targets) {
    PsiMethod @NotNull [] methods = SuperMethodWarningUtil.getTargetMethodCandidates(method, targets);
    return !(methods.length == 1 && methods[0] == method);
  }

  @Override
  public @NotNull @Unmodifiable List<ModPlanOption<?>> options() {
    return List.of(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS, ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF);
  }

  /**
   * @return whether the switch has an explicit value, or the names answer this method, which an answer to this question adds
   */
  @Override
  public boolean isAnswered(@NotNull ModPlanOptionValues currentValues) {
    return currentValues.has(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) ||
           myMethodName != null && currentValues.answeredKeys(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF).contains(myMethodName);
  }

  /**
   * @return whether the base methods of this method are deleted. An explicit false keeps them. Else they go if the chosen names hold this
   * method; they stay if it was asked about and not chosen, or if the names hold only other methods; with no names, the switch decides,
   * explicit or by default.
   */
  boolean shouldDeleteSuperMethods(@NotNull ModPlanOptionValues answers) {
    if (answers.has(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS) && !answers.get(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS)) {
      return false;
    }
    Set<String> chosenNames = answers.chosenKeys(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF);
    if (myMethodName != null && chosenNames.contains(myMethodName)) return true;
    if (myMethodName != null && answers.answeredKeys(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF).contains(myMethodName)) return false;
    if (!chosenNames.isEmpty()) return false;
    return answers.get(ModPlanJavaSafeDeleteOptions.DELETE_SUPER_METHODS);
  }

  @RequiresEdt
  @Override
  public @Nullable ModPlanOptionValues ask(@NotNull Project project, @NotNull ModPlanOptionValues currentValues) {
    if (!(myMethod.element() instanceof PsiMethod method)) return null;
    PsiMethod[] chosenMethods = SuperMethodWarningUtil.checkSuperMethods(method, List.of(method));
    if (chosenMethods.length == 0) return null;
    // As in the legacy flow, a base method that cannot be written, such as one of a library, ends it before anything is searched.
    if (!CommonRefactoringUtil.checkReadOnlyStatusRecursively(project, List.of(chosenMethods), true)) return null;
    if (myMethodName == null) return currentValues;
    boolean deletesSuperMethods = !(chosenMethods.length == 1 && chosenMethods[0] == method);
    return currentValues.withAnswers(ModPlanJavaSafeDeleteOptions.SUPER_METHODS_OF, List.of(myMethodName),
                                     deletesSuperMethods ? List.of(myMethodName) : List.of());
  }
}
