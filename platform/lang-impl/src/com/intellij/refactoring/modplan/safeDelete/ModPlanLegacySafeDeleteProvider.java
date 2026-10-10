// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.model.SymbolPointer;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanFinalStep;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.safeDelete.impl.ModPlanSettingsSearchStorage;
import com.intellij.refactoring.safeDelete.NonCodeUsageSearchInfo;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegateBase;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs a plain legacy {@link SafeDeleteProcessorDelegate} on the ModPlan engine, by composition: the search and the writes are the legacy
 * ones, and nothing asks the user. It serves the delegates that have no provider of their own, such as the one of the related files; Java
 * and Kotlin have theirs.
 * <p>
 * <b>How a delegate opts in:</b> its {@link SafeDeleteProcessorDelegate#asModPlan} returns
 * {@code new ModPlanLegacySafeDeleteProvider(this)}. The delegate must not ask the user in {@code getElementsToSearch},
 * {@code findConflicts}, {@code preprocessUsages} and {@code prepareForDeletion}: a delegate that does returns null and stays on the legacy
 * flow. A subclass of {@code JavaSafeDeleteProcessor} that opts in overrides {@code getElementsToSearch}, whose inherited code asks on the
 * EDT. The search options are kept in the common settings of the safe delete.
 * <p>
 * <b>Refusals:</b> a legacy delegate that returns no adapter is refused with {@code NOT_SUPPORTED}; so is a usage that changes code and
 * does not implement {@link ModPlanSafeDeleteUsage}. A usage that returns no edit is a conflict.
 * <p>
 * <b>Known differences from the legacy flow:</b> only this adapter preprocesses the usages, per element, where the legacy flow passes the
 * usages of all the elements through {@code preprocessUsages} of every delegate; the preparation of an element runs among the edits, not
 * after all of them; the events also list a file that a usage deletes.
 */
public final class ModPlanLegacySafeDeleteProvider implements ModPlanSafeDeleteProvider {
  private final @NotNull SafeDeleteProcessorDelegate myDelegate;

  /**
   * @param delegate the legacy delegate this provider runs
   */
  public ModPlanLegacySafeDeleteProvider(@NotNull SafeDeleteProcessorDelegate delegate) {
    myDelegate = delegate;
  }

  @Override
  public boolean isApplicable(@NotNull SymbolPointer subject) {
    PsiElement element = subject.element();
    return element != null && myDelegate.handlesElement(element);
  }

  /**
   * For a requested subject it runs the first legacy step, as the interactive safe delete does before its dialog:
   * {@code getElementsToSearch}, and refuses the subject if there is nothing to search; then it adds what is deleted with the subject, and
   * asks the search options.
   * <p>
   * With a request that {@link ModPlanSafeDeleteOptions#SEARCH_RELATED searches no related elements} {@code getElementsToSearch} is not
   * called and the elements to search are the target.
   */
  @Override
  public @NotNull ModPlanChain<DeletionDecision> prepareRequest(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull SymbolPointer subject,
    @NotNull List<SymbolPointer> deletedSubjects
  ) {
    PsiElement target = subject.element();
    if (target == null) return chainBuilder.passValue(DeletionDecision.REFUSED);
    if (!chainBuilder.getRequest().subjects().contains(subject)) {
      return chainBuilder.passValue(DeletionDecision.DELETED);
    }
    Collection<? extends PsiElement> elements =
      chainBuilder.getRequest().optionValues().get(ModPlanSafeDeleteOptions.SEARCH_RELATED)
      ? myDelegate.getElementsToSearch(target, ModPlanSafeDeleteUsageInfoUtil.deletedWith(target, deletedSubjects))
      : List.of(target);
    if (elements == null || elements.isEmpty()) return chainBuilder.passValue(DeletionDecision.REFUSED);
    List<SymbolPointer> alsoDeleted = alsoDeletedOf(target, elements);
    return chainBuilder.ask(ModPlanSafeDeleteUsageInfoUtil.searchQuestion(target, deletedSubjects, ModPlanSettingsSearchStorage.INSTANCE),
                            answered -> answered.passValue(DeletionDecision.deletedWith(alsoDeleted)));
  }

  /**
   * Runs the second legacy step, as the interactive safe delete does before its dialog: {@code getAdditionalElementsToDelete} of every
   * element to search. A question of the legacy delegate is answered by its default.
   */
  private static @NotNull List<SymbolPointer> alsoDeletedOf(@NotNull PsiElement target, @NotNull Collection<? extends PsiElement> elements) {
    Set<PsiElement> resultElements = new LinkedHashSet<>(elements);
    resultElements.remove(target);
    for (PsiElement element : elements) {
      SafeDeleteProcessorDelegate handler = legacyHandlerOf(element);
      if (handler == null) continue;
      SafeDeleteProcessorDelegate.AdditionalElementsData data = handler.getAdditionalElementsToDelete(element, elements);
      switch (data.policy()) {
        case SafeDeleteProcessorDelegate.ConfirmationPolicy.DontAsk _ -> resultElements.addAll(data.elements());
        case SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask ask -> {
          if (ask.deleteByDefault()) {
            resultElements.addAll(data.elements());
          }
        }
      }
    }
    return ContainerUtil.map(resultElements, SymbolPointer::of);
  }

  @Override
  public @NotNull ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> analyze(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull SymbolPointer subject,
    @NotNull List<SymbolPointer> deletedSubjects
  ) {
    // The adapter asks nothing, so the search and the edits run in the step at the end of the run, after every question. The usages of
    // the delegate are made and used in that step, so their fields need no smart pointers.
    return chainBuilder.finish(request -> {
      PsiElement target = subject.element();
      if (target == null) return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
      PsiElement[] elementsToDelete = ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletedSubjects);
      List<UsageInfo> rawUsages = Collections.synchronizedList(new ArrayList<>());
      NonCodeUsageSearchInfo info = myDelegate.findUsages(target, elementsToDelete, rawUsages);
      List<UsageInfo> foundUsages =
        ModPlanSafeDeleteUsageInfoUtil.withNonCodeUsages(info, rawUsages, request.project(), request.optionValues());
      UsageInfo[] foundUsagesArray = foundUsages.toArray(UsageInfo.EMPTY_ARRAY);

      MultiMap<PsiElement, String> legacyMessages = new MultiMap<>();
      myDelegate.findConflicts(target, elementsToDelete, foundUsagesArray, legacyMessages);
      @SuppressWarnings("deprecation")
      Collection<String> messages = myDelegate instanceof SafeDeleteProcessorDelegateBase base
                                    ? base.findConflicts(target, elementsToDelete, foundUsagesArray)
                                    : myDelegate.findConflicts(target, elementsToDelete);
      if (messages != null) {
        legacyMessages.putValues(target, messages);
      }

      UsageInfo[] processedUsages = myDelegate.preprocessUsages(request.project(), foundUsagesArray);
      if (processedUsages == null) {
        return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
      }
      List<UsageInfo> preprocessedUsages = List.of(UsageViewUtil.removeDuplicatedUsages(processedUsages));
      return ModPlanSafeDeleteUsageInfoUtil.analyzeUsages(
        target, foundUsages, preprocessedUsages, legacyMessages, ModPlanEdit.update(target, copy -> myDelegate.prepareForDeletion(copy)));
    });
  }

  /**
   * @return the first legacy delegate in the extension order that handles {@code element}; it is the owner of the element in the legacy
   * safe delete
   */
  @ApiStatus.Internal
  public static @Nullable SafeDeleteProcessorDelegate legacyHandlerOf(@NotNull PsiElement element) {
    return SafeDeleteProcessorDelegate.EP_NAME.findFirstSafe(delegate -> delegate.handlesElement(element));
  }
}
