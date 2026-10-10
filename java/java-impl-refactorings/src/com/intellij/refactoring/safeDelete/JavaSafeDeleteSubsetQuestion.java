// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteMemberCalleeUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteOverridingMethodUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteParameterCallHierarchyUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.concurrency.annotations.RequiresEdt;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * The questions that choose a subset: which overriding methods and which unused callees to delete, and in which callers to delete the
 * parameter that only passes on the deleted one. They show the dialogs of the interactive safe delete, and the answer is the external
 * names of the chosen elements, one per line, see {@link ModPlanJavaSafeDeleteOptions#OVERRIDERS_TO_DELETE}, and the offered ones that are
 * not chosen, see {@link ModPlanOptionValues#withAnswers}. The names of an earlier answer stay: the answers of several questions add up to
 * one list, so a question is answered when the list answers all of its own elements. The switch, such as
 * {@link ModPlanJavaSafeDeleteOptions#DELETE_OVERRIDERS}, is the general answer of a request, and the dialog does not set it.
 */
final class JavaSafeDeleteSubsetQuestion implements ModPlanQuestion.Custom {
  private final @NotNull ModPlanOption<Boolean> mySwitch;
  private final @NotNull ModPlanOption<String> myNames;
  /** The external names of the elements that the question offers. */
  private final @NotNull @Unmodifiable List<String> myOfferedNames;
  private final @NotNull Function<Project, @Nullable List<String>> myChoose;

  private JavaSafeDeleteSubsetQuestion(@NotNull ModPlanOption<Boolean> aSwitch,
                                       @NotNull ModPlanOption<String> names,
                                       @NotNull List<String> offeredNames,
                                       @NotNull Function<Project, @Nullable List<String>> choose) {
    mySwitch = aSwitch;
    myNames = names;
    myOfferedNames = offeredNames;
    myChoose = choose;
  }

  /** The dialog of the overriding methods, the usages of the elements that are unused after the deletion. */
  static @NotNull JavaSafeDeleteSubsetQuestion overriders(@NotNull List<SafeDeleteOverridingMethodUsageInfo> overridingUsages) {
    return new JavaSafeDeleteSubsetQuestion(
      ModPlanJavaSafeDeleteOptions.DELETE_OVERRIDERS, ModPlanJavaSafeDeleteOptions.OVERRIDERS_TO_DELETE,
      ContainerUtil.mapNotNull(overridingUsages, SafeDeleteOverridingMethodUsageInfo::getExternalName), project -> {
        List<SafeDeleteOverridingMethodUsageInfo> restoredUsages = ReadAction.computeBlocking(() -> ContainerUtil.filter(
          overridingUsages, usage -> usage.getOverridingMethod() != null && usage.findReferencedElement() instanceof PsiMethod));
        if (restoredUsages.isEmpty()) return null;
        OverridingMethodsDialog dialog = new OverridingMethodsDialog(project, restoredUsages);
        if (!dialog.showAndGet()) return null;
        return ContainerUtil.mapNotNull(dialog.getSelected(), info -> ((SafeDeleteOverridingMethodUsageInfo)info).getExternalName());
      });
  }

  /** The chooser of the unused callees of {@code member}. */
  static @NotNull JavaSafeDeleteSubsetQuestion callees(@NotNull PsiMember member, @NotNull List<SafeDeleteMemberCalleeUsageInfo> calleeUsages) {
    SymbolPointer owner = SymbolPointer.of(member);
    return new JavaSafeDeleteSubsetQuestion(
      ModPlanJavaSafeDeleteOptions.DELETE_UNUSED_CALLEES, ModPlanJavaSafeDeleteOptions.CALLEES_TO_DELETE,
      namesOf(calleeUsages, UsageInfo::getElement), project -> {
        if (!(owner.element() instanceof PsiMember currentMember)) return null;
        List<SafeDeleteMemberCalleeUsageInfo> restoredUsages =
          ReadAction.computeBlocking(() -> JavaSafeDeleteProcessor.liveCalleeUsages(calleeUsages));
        if (restoredUsages.isEmpty()) return null;
        List<UsageInfo> chosenUsages = new ArrayList<>();
        SafeDeleteJavaCalleeChooser chooser = new SafeDeleteJavaCalleeChooser(currentMember, project, chosenUsages) {
          @Override
          protected @NotNull List<SafeDeleteMemberCalleeUsageInfo> getTopLevelItems() {
            return restoredUsages;
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) return null;
        return namesOf(chosenUsages, UsageInfo::getElement);
      });
  }

  /**
   * The chooser of the callers whose parameter only passes on the deleted parameter or field, as the legacy flow shows it for
   * {@code delegatingUsages}, the usages of the first caller level.
   */
  static @NotNull JavaSafeDeleteSubsetQuestion callers(@NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> delegatingUsages) {
    SafeDeleteParameterCallHierarchyUsageInfo delegatingUsage = delegatingUsages.getFirst();
    PsiParameter parameter = delegatingUsage.findReferencedElement();
    PsiMethod calledMethod = delegatingUsage.getCalledMethod();
    int parameterIndex = parameter == null || calledMethod == null ? -1 : calledMethod.getParameterList().getParameterIndex(parameter);
    return new JavaSafeDeleteSubsetQuestion(
      ModPlanJavaSafeDeleteOptions.DELETE_PARAMETER_IN_CALLERS, ModPlanJavaSafeDeleteOptions.CALLERS_TO_DELETE_PARAMETER_IN,
      namesOf(delegatingUsages, SafeDeleteParameterCallHierarchyUsageInfo::getCallerMethod), project -> {
        List<SafeDeleteParameterCallHierarchyUsageInfo> restoredUsages =
          ReadAction.computeBlocking(() -> JavaSafeDeleteProcessor.liveCallerUsages(delegatingUsages));
        if (restoredUsages.isEmpty() || !(restoredUsages.getFirst().getCalledMethod() instanceof PsiMethod method)) return null;
        List<UsageInfo> chosenUsages = new ArrayList<>();
        SafeDeleteJavaCallerChooser chooser = new SafeDeleteJavaCallerChooser(method, project, chosenUsages) {
          @Override
          protected @NotNull List<SafeDeleteParameterCallHierarchyUsageInfo> getTopLevelItems() {
            return restoredUsages;
          }

          @Override
          protected int getParameterIdx() {
            return parameterIndex;
          }

          @Override
          protected PsiParameter getParameterInCaller(PsiMethod calledMethod, int paramIdx, PsiMethod caller) {
            SafeDeleteParameterCallHierarchyUsageInfo callerUsage =
              ContainerUtil.find(restoredUsages, usage -> caller.equals(usage.getCallerMethod()));
            PsiParameter parameterInCaller = callerUsage == null ? null : callerUsage.getParameterInCaller();
            return parameterInCaller != null ? parameterInCaller : super.getParameterInCaller(calledMethod, paramIdx, caller);
          }
        };
        TreeUtil.expand(chooser.getTree(), 2);
        if (!chooser.showAndGet()) return null;
        return namesOf(ContainerUtil.filterIsInstance(chosenUsages, SafeDeleteParameterCallHierarchyUsageInfo.class),
                       SafeDeleteParameterCallHierarchyUsageInfo::getCallerMethod);
      });
  }

  private static <T extends UsageInfo> @NotNull List<String> namesOf(@NotNull List<? extends T> usages,
                                                                     @NotNull Function<? super T, @Nullable PsiElement> elementOf) {
    return ReadAction.compute(() -> {
      List<String> names = new ArrayList<>();
      for (T usage : usages) {
        PsiElement element = elementOf.apply(usage);
        String name = element == null ? null : JavaSafeDeleteProcessor.externalNameOf(element);
        if (name != null) names.add(name);
      }
      return names;
    });
  }

  @Override
  public @NotNull @Unmodifiable List<ModPlanOption<?>> options() {
    return List.of(mySwitch, myNames);
  }

  /**
   * @return whether the switch has an explicit value, or the names answer every element that this question offers. A question that offers
   * a new element is asked, also if an earlier question offered some of its elements
   */
  @Override
  public boolean isAnswered(@NotNull ModPlanOptionValues currentValues) {
    if (currentValues.has(mySwitch)) return true;
    return !myOfferedNames.isEmpty() && currentValues.answeredKeys(myNames).containsAll(myOfferedNames);
  }

  /**
   * @return the external names of the chosen elements; null chooses all the offered ones. An explicit false chooses none. Else the
   * chosen names decide if they hold an offered element; an element that was offered and not chosen is not chosen; a list of other names
   * only chooses none; with no names, the switch decides, explicit or by default.
   */
  @Nullable Set<String> chosenNamesIn(@NotNull ModPlanOptionValues answers) {
    if (answers.has(mySwitch) && !answers.get(mySwitch)) return Set.of();
    Set<String> chosenNames = answers.chosenKeys(myNames);
    if (ContainerUtil.exists(myOfferedNames, chosenNames::contains)) return chosenNames;
    if (ContainerUtil.exists(myOfferedNames, answers.answeredKeys(myNames)::contains)) return Set.of();
    if (!chosenNames.isEmpty()) return chosenNames;
    return answers.get(mySwitch) ? null : Set.of();
  }

  @RequiresEdt
  @Override
  public @Nullable ModPlanOptionValues ask(@NotNull Project project, @NotNull ModPlanOptionValues currentValues) {
    List<String> chosenNames = myChoose.apply(project);
    if (chosenNames == null) return null;
    return currentValues.withAnswers(myNames, myOfferedNames, chosenNames);
  }
}
