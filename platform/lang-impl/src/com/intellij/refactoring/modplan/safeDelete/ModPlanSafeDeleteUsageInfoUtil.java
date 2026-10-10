// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteCustomUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteReferenceUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteUsageInfo;
import com.intellij.refactoring.util.RefactoringUIUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Static helpers for the providers that analyze with the legacy {@code UsageInfo}s: the adapter of the legacy delegates and the Java and
 * Kotlin providers.
 */
@ApiStatus.Internal
public final class ModPlanSafeDeleteUsageInfoUtil {
  private ModPlanSafeDeleteUsageInfoUtil() {
  }

  /** @return the live elements of {@code subjects}, in their order */
  public static @NotNull PsiElement @NotNull [] elementsOf(@NotNull List<SymbolPointer> subjects) {
    return ContainerUtil.mapNotNull(subjects, SymbolPointer::element).toArray(PsiElement.EMPTY_ARRAY);
  }

  /**
   * @param deletedSubjects the subjects that are deleted so far, see {@link ModPlanSafeDeleteProvider#prepareRequest}
   * @return the form about the search in comments and in non-code files, see {@link #searchForm}; null for a file that holds another
   * deleted element, as the legacy dialog is made for its classes, not for the file
   */
  public static @Nullable ModPlanQuestion.Form searchQuestion(@NotNull PsiElement target,
                                                             @NotNull List<SymbolPointer> deletedSubjects,
                                                             @NotNull ModPlanQuestion.Storage storage) {
    if (target instanceof PsiFile) {
      for (PsiElement deletedElement : deletedWith(target, deletedSubjects)) {
        if (deletedElement != target && deletedElement.getContainingFile() == target) {
          return null;
        }
      }
    }
    return searchForm(storage);
  }

  /**
   * @return the form of {@link ModPlanSafeDeleteOptions#SEARCH_IN_COMMENTS} and {@link ModPlanSafeDeleteOptions#SEARCH_IN_NON_CODE}, in the
   * order of the legacy dialog, remembered in {@code storage}
   */
  public static @NotNull ModPlanQuestion.Form searchForm(@NotNull ModPlanQuestion.Storage storage) {
    return ModPlanQuestion.Form.of(List.of(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE), storage);
  }

  /**
   * @param deletedSubjects the subjects that are deleted so far, see {@link ModPlanSafeDeleteProvider#prepareRequest}
   * @return the deleted elements, which the legacy delegate takes as all the selected elements; {@code target} alone if they have no
   * live element left
   */
  public static @NotNull List<PsiElement> deletedWith(@NotNull PsiElement target, @NotNull List<SymbolPointer> deletedSubjects) {
    PsiElement[] deletedElements = elementsOf(deletedSubjects);
    return deletedElements.length > 0 ? List.of(deletedElements) : List.of(target);
  }

  /**
   * @param info the result of {@code findUsages}; null adds nothing
   * @param usages the usages that {@code findUsages} found
   * @param optionValues the options with {@link ModPlanSafeDeleteOptions#SEARCH_IN_COMMENTS} and
   *                     {@link ModPlanSafeDeleteOptions#SEARCH_IN_NON_CODE}
   * @return {@code usages} with the usages in comments and in non-code files of the elements of {@code info}, without duplicates
   */
  public static @NotNull List<UsageInfo> withNonCodeUsages(@Nullable NonCodeUsageSearchInfo info,
                                                           @NotNull List<UsageInfo> usages,
                                                           @NotNull Project project,
                                                           @NotNull ModPlanOptionValues optionValues) {
    List<UsageInfo> resultUsages = Collections.synchronizedList(new ArrayList<>(usages));
    if (info != null) {
      boolean searchNonCode = optionValues.get(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE);
      boolean searchInComments = optionValues.get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS);
      for (PsiElement element : info.getElementsToSearch()) {
        SafeDeleteProcessor.addNonCodeUsages(element, GlobalSearchScope.projectScope(project), resultUsages,
                                             info.getInsideDeletedCondition(), searchNonCode, searchInComments);
      }
    }
    return List.of(UsageViewUtil.removeDuplicatedUsages(resultUsages.toArray(UsageInfo.EMPTY_ARRAY)));
  }

  /**
   * @param foundUsages the usages of the target
   * @param preprocessedUsages the usages that the delegate left to process
   * @param initialConflictMessages the messages of the legacy conflicts by the element they are about; they all become broken conflicts
   * @param preparation the preparation of the target, which runs among the edits; null if there is none
   * @return the edits and conflicts of the usages: a usage that still needs the target is a broken conflict, a usage in a comment or a
   * non-code file is a non-code usage, and a usage that changes code is an edit, or a broken conflict if it returns no edit.
   * {@code Refused} if a usage that changes code is not a {@link ModPlanSafeDeleteUsage}
   */
  public static @NotNull ModPlanAnalysisResult analyzeUsages(@NotNull PsiElement target,
                                                             @NotNull List<UsageInfo> foundUsages,
                                                             @NotNull List<UsageInfo> preprocessedUsages,
                                                             @NotNull MultiMap<PsiElement, String> initialConflictMessages,
                                                             @Nullable ModPlanEdit preparation) {
    ModPlanConflicts.Builder initialConflicts = ModPlanConflicts.builder();
    for (var entry : initialConflictMessages.entrySet()) {
      for (String message : entry.getValue()) {
        initialConflicts.broken(entry.getKey(), message);
      }
    }

    ModPlanConflicts.Builder conflicts = ModPlanConflicts.builder();
    Set<UsageInfo> blockingUsages = new LinkedHashSet<>(foundUsages);
    blockingUsages.addAll(preprocessedUsages);
    for (UsageInfo usage : blockingUsages) {
      PsiElement element = usage.getElement();
      if (element != null && !usage.isNonCodeUsage && isBlocking(usage)) {
        PsiElement referencedElement = ((SafeDeleteReferenceUsageInfo)usage).findReferencedElement();
        conflicts.broken(element, SafeDeleteProcessor.unsafeUsageMessage(referencedElement != null ? referencedElement : target, element));
      }
    }

    List<ModPlanEdit> edits = new ArrayList<>();
    for (UsageInfo usage : preprocessedUsages) {
      PsiElement element = usage.getElement();
      if (element == null) continue;
      if (usage.isNonCodeUsage) {
        PsiElement referencedElement = usage instanceof SafeDeleteUsageInfo info ? info.findReferencedElement() : null;
        String message = RefactoringBundle.message(
          "non.code.usage.that.is.not.safe.to.delete",
          RefactoringUIUtil.getDescription(referencedElement != null ? referencedElement : target, true));
        conflicts.nonCodeUsage(element, usage.getRangeInElement(), message);
      }
      else if (!isBlocking(usage) && usage instanceof SafeDeleteCustomUsageInfo) {
        if (!(usage instanceof ModPlanSafeDeleteUsage editableUsage)) {
          return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED);
        }
        ModPlanEdit edit = editableUsage.asModPlanEdit();
        if (edit != null) {
          edits.add(edit);
        }
        else {
          PsiElement referencedElement = usage instanceof SafeDeleteUsageInfo info ? info.findReferencedElement() : null;
          conflicts.broken(element,
                           SafeDeleteProcessor.unsafeUsageMessage(referencedElement != null ? referencedElement : target, element));
        }
      }
    }
    if (preparation != null) {
      edits.add(preparation);
    }
    return new ModPlanAnalysisResult.Found(edits, initialConflicts.build().merge(conflicts.build()));
  }

  private static boolean isBlocking(@NotNull UsageInfo usage) {
    return usage instanceof SafeDeleteReferenceUsageInfo info && !info.isSafeDelete();
  }
}
