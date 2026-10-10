// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.safeDelete;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.PsiPackageAccessibilityStatement;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanFinalStep;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.impl.ModPlanSubjectConfirmation;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.safeDelete.ModPlanLegacySafeDeleteProvider;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteOptions;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteProvider;
import com.intellij.refactoring.modplan.safeDelete.ModPlanSafeDeleteUsageInfoUtil;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteModuleStatementsUsageInfo;
import com.intellij.refactoring.safeDelete.usageInfo.SafeDeleteSealedPermitsUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.usageView.UsageViewUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The safe delete of Java elements on the ModPlan engine: it runs the code of {@link JavaSafeDeleteProcessor}, and its options answer the
 * questions that the interactive safe delete asks in dialogs. It owns an element if {@code JavaSafeDeleteProcessor} itself is the first
 * legacy delegate that handles it, so a subclass of the processor, such as the one of Bazel, stays on the legacy flow.
 * <p>
 * A usage that removes an external annotation has no edit, as the legacy write is a command of the annotations manager, so it is a
 * conflict, and the write leaves the annotation. An element that no annotations file names has nothing to remove, so it is no conflict.
 * A package is deleted with its directories, which an edit cannot express, so the legacy flow deletes it.
 * <p>
 * <b>Known differences from the legacy flow:</b>
 * <ul>
 *   <li>the chooser of the unused callees and the dialog of the overriding methods of several elements are asked one element at a time,
 *   and their answers add up, where the legacy flow shows one dialog for all the elements;</li>
 *   <li>the permits list of a sealed class is written in a fixed order, the deleted classes leave it and then the heirs of a deleted class
 *   join it, where the legacy flow writes in the order of the files and can drop {@code sealed} if the file of the parent sorts after the
 *   file of the heir;</li>
 *   <li>the usages are preprocessed per element, not over the usages of all the elements; the overriding methods to delete and the
 *   {@code @Override} that stays are the same, as an overrider that matters to an element is one of its own overriders;</li>
 *   <li>the preparation of an element runs among the edits, not after all of them;</li>
 *   <li>the events list every deleted element, usages included;</li>
 *   <li>the {@code exports} and {@code opens} of a package that has no class left are non-code usages, where the legacy flow shows a
 *   notification that offers to delete them;</li>
 *   <li>the search options of several elements are kept by the first element, where the legacy dialog uses the common settings; a file
 *   asks nothing about them, where the legacy dialog asks for its classes.</li>
 * </ul>
 */
final class ModPlanJavaSafeDeleteProvider implements ModPlanSafeDeleteProvider {
  @Override
  public boolean isApplicable(@NotNull SymbolPointer subject) {
    PsiElement element = subject.element();
    return element != null && isOwnedByJava(element);
  }

  @Override
  public boolean isLegacyOnly(@NotNull SymbolPointer subject) {
    return subject.element() instanceof PsiPackage;
  }

  /**
   * For a requested subject it runs the first legacy steps, as the interactive safe delete does before its dialog: the chain of
   * {@code getElementsToSearch}, the search options, and the accessors of a field, and adds what is deleted with the subject. A subject
   * with nothing to search is refused. A default constructor has no text, so its class replaces it, as in the legacy flow, with the search
   * options of the class.
   * <p>
   * The questions go in this order: the ones of {@code getElementsToSearch}, such as the base methods to delete, then the search
   * options, then the accessors.
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
    if (target instanceof PsiMethod method && method.isDefaultConstructor() && method.getContainingClass() instanceof PsiClass aClass) {
      SymbolPointer classSubject = SymbolPointer.of(aClass);
      ModPlanQuestion classSearchQuestion =
        ModPlanSafeDeleteUsageInfoUtil.searchQuestion(aClass, deletedSubjects, new JavaSearchStorage(classSubject));
      return chainBuilder.ask(classSearchQuestion, searched -> searched.passValue(DeletionDecision.replacedBy(List.of(classSubject))));
    }
    ModPlanQuestion searchQuestion = ModPlanSafeDeleteUsageInfoUtil.searchQuestion(target, deletedSubjects, new JavaSearchStorage(subject));
    if (!chainBuilder.getRequest().optionValues().get(ModPlanSafeDeleteOptions.SEARCH_RELATED)) return withSearchOptions(chainBuilder, List.of(target), searchQuestion, subject);
    List<PsiElement> deletedWithTarget = ModPlanSafeDeleteUsageInfoUtil.deletedWith(target, deletedSubjects);
    return JavaSafeDeleteProcessor.elementsToSearch(chainBuilder, target, null, deletedWithTarget)
      .andThen((afterElements, elements) -> withSearchOptions(afterElements, elements, searchQuestion, subject));
  }

  private static @NotNull ModPlanChain<DeletionDecision> withSearchOptions(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull List<PsiElement> elements,
    @Nullable ModPlanQuestion searchQuestion,
    @NotNull SymbolPointer targetSubject
  ) {
    if (elements.isEmpty()) return chainBuilder.passValue(DeletionDecision.REFUSED);
    List<SymbolPointer> elementSubjects = ContainerUtil.map(elements, SymbolPointer::of);
    return chainBuilder.ask(searchQuestion, searched -> withAccessors(searched, targetSubject, elementSubjects));
  }

  //just for compatibility until more providers will use ModPlan
  private static boolean isOwnedByJava(@NotNull PsiElement element) {
    SafeDeleteProcessorDelegate firstDelegate = ModPlanLegacySafeDeleteProvider.legacyHandlerOf(element);
    return firstDelegate != null && firstDelegate.getClass() == JavaSafeDeleteProcessor.class;
  }

  /**
   * The second legacy step: the additional elements of every element to search, with the question whether to delete the accessors of a
   * field, see {@link ModPlanJavaSafeDeleteOptions#DELETE_ACCESSORS}.
   *
   * @return the chain that ends with the additional elements, which are deleted too
   */
  private static @NotNull ModPlanChain<DeletionDecision> withAccessors(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull SymbolPointer targetSubject,
    @NotNull List<SymbolPointer> elementSubjects
  ) {
    List<PsiElement> elements = List.of(ModPlanSafeDeleteUsageInfoUtil.elementsOf(elementSubjects));
    List<@NlsSafe String> accessorMessages = new ArrayList<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiField &&
          additionalOf(element, elements).policy() instanceof SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask ask) {
        accessorMessages.add(ask.message());
      }
    }
    @NlsSafe String accessorNote = String.join("\n", accessorMessages);
    PsiElement targetElement = targetSubject.element();
    ModPlanSubjectConfirmation accessorQuestion = accessorMessages.isEmpty() ? null : new ModPlanSubjectConfirmation(
      ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS, ModPlanJavaSafeDeleteOptions.ACCESSORS_OF,
      targetElement == null ? null : JavaSafeDeleteProcessor.externalNameOf(targetElement),
      project -> Messages.showYesNoDialog(project, accessorNote, RefactoringBundle.message("safe.delete.title"),
                                          Messages.getQuestionIcon()) == Messages.YES);
    return chainBuilder.ask(accessorQuestion, answered -> {
      PsiElement target = targetSubject.element();
      if (target == null) return answered.passValue(DeletionDecision.REFUSED);
      List<PsiElement> currentElements = List.of(ModPlanSafeDeleteUsageInfoUtil.elementsOf(elementSubjects));
      ModPlanOptionValues answers = answered.getRequest().optionValues();
      boolean deletesAccessors =
        accessorQuestion == null ? answers.get(ModPlanJavaSafeDeleteOptions.DELETE_ACCESSORS) : accessorQuestion.isConfirmed(answers);
      Set<PsiElement> resultElements = new LinkedHashSet<>(currentElements);
      resultElements.remove(target);
      for (PsiElement element : currentElements) {
        SafeDeleteProcessorDelegate.AdditionalElementsData data = additionalOf(element, currentElements);
        switch (data.policy()) {
          case SafeDeleteProcessorDelegate.ConfirmationPolicy.DontAsk _ -> resultElements.addAll(data.elements());
          case SafeDeleteProcessorDelegate.ConfirmationPolicy.Ask ask -> {
            boolean delete = element instanceof PsiField ? deletesAccessors : ask.deleteByDefault();
            if (delete) {
              resultElements.addAll(data.elements());
            }
          }
        }
      }
      return answered.passValue(DeletionDecision.deletedWith(ContainerUtil.map(resultElements, SymbolPointer::of)));
    });
  }

  /**
   * The additional elements of the deletion of {@code element}, which only the processor of Java gives: an element of another language in
   * the same search, such as a Kotlin parameter, has none.
   */
  private static @NotNull SafeDeleteProcessorDelegate.AdditionalElementsData additionalOf(
    @NotNull PsiElement element,
    @NotNull Collection<? extends PsiElement> elements
  ) {
    return isOwnedByJava(element) ? JavaSafeDeleteProcessor.additionalElementsOf(element, elements)
                                  : SafeDeleteProcessorDelegate.AdditionalElementsData.NONE;
  }

  /** @return the usages of {@code target} in the deletion of {@code elementsToDelete}: {@code findUsages} and the non-code search */
  private static @NotNull List<UsageInfo> search(@NotNull PsiElement target, @NotNull PsiElement @NotNull [] elementsToDelete,
                                                 @NotNull ModPlanRequest request) {
    Project project = request.project();
    List<UsageInfo> rawUsages = Collections.synchronizedList(new ArrayList<>());
    NonCodeUsageSearchInfo info = JavaSafeDeleteProcessor.findUsagesOf(target, elementsToDelete, rawUsages);
    return ModPlanSafeDeleteUsageInfoUtil.withNonCodeUsages(info, rawUsages, project, request.optionValues());
  }

  /**
   * Searches the usages of the target, and preprocesses them with the chain of {@code preprocessUsages}, which asks which overriding
   * methods and unused callees to delete, and in which callers to delete the parameter that only passes on the target.
   */
  @Override
  public @NotNull ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> analyze(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull SymbolPointer subject,
    @NotNull List<SymbolPointer> deletedSubjects
  ) {
    PsiElement target = subject.element();
    if (target == null) return chainBuilder.finish(ignored -> new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE));
    // The questions need the usages, so the search runs before them; the conflicts and the edits run in the step at the end. The usages
    // outlive the questions that come later in the run: they keep their elements in smart pointers.
    List<UsageInfo> foundUsages = search(target, ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletedSubjects), chainBuilder.getRequest());
    return JavaSafeDeleteProcessor.preprocessedUsages(chainBuilder, foundUsages.toArray(UsageInfo.EMPTY_ARRAY))
      .andThen((afterUsages, processedUsages) ->
                 afterUsages.finish(ignored -> analysisOf(subject, deletedSubjects, foundUsages, processedUsages)));
  }

  private static @NotNull ModPlanAnalysisResult analysisOf(@NotNull SymbolPointer subject,
                                                           @NotNull List<SymbolPointer> deletedSubjects,
                                                           @NotNull List<UsageInfo> foundUsages,
                                                           @NotNull List<UsageInfo> processedUsages) {
    PsiElement target = subject.element();
    if (target == null) return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
    PsiElement[] elementsToDelete = ModPlanSafeDeleteUsageInfoUtil.elementsOf(deletedSubjects);
    MultiMap<PsiElement, String> legacyMessages = new MultiMap<>();
    JavaSafeDeleteProcessor.collectConflicts(target, elementsToDelete, foundUsages.toArray(UsageInfo.EMPTY_ARRAY), legacyMessages);

    ModPlanConflicts.Builder moduleStatementNonCodeUsages =ModPlanConflicts.builder();
    List<UsageInfo> codeUsages = new ArrayList<>();
    for (UsageInfo usage : processedUsages) {
      if (usage instanceof SafeDeleteModuleStatementsUsageInfo moduleStatementsUsage) {
        String message = JavaRefactoringBundle.message("move.classes.or.packages.unused.exports.notification.title", 1);
        for (PsiPackageAccessibilityStatement statement : moduleStatementsUsage.getModuleStatements()) {
          moduleStatementNonCodeUsages.nonCodeUsage(statement, null, message);
        }
      }
      else {
        codeUsages.add(usage);
      }
    }
    List<UsageInfo> preprocessedUsages = SafeDeleteSealedPermitsUsageInfo.withAdditions(codeUsages);
    preprocessedUsages = List.of(UsageViewUtil.removeDuplicatedUsages(preprocessedUsages.toArray(UsageInfo.EMPTY_ARRAY)));
    // A light element, such as the parameter of a compact constructor that is deleted with its record component, has no text to prepare.
    ModPlanEdit preparation = target.isPhysical() ? ModPlanEdit.update(target, JavaSafeDeleteProcessor::prepareElementForDeletion) : null;
    ModPlanAnalysisResult
      analysis = ModPlanSafeDeleteUsageInfoUtil.analyzeUsages(target, foundUsages, preprocessedUsages, legacyMessages, preparation);
    if (!(analysis instanceof ModPlanAnalysisResult.Found(List<ModPlanEdit> edits, ModPlanConflicts conflicts))) return analysis;
    return new ModPlanAnalysisResult.Found(edits, conflicts.merge(moduleStatementNonCodeUsages.build()));
  }

  /** Keeps the search options in the settings that {@link JavaSafeDeleteProcessor} uses for the kind of the element. */
  private static final class JavaSearchStorage implements ModPlanQuestion.Storage {
    private final @NotNull SymbolPointer mySubject;

    private JavaSearchStorage(@NotNull SymbolPointer subject) {
      mySubject = subject;
    }

    @Override
    public @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues optionValues) {
      PsiElement element = mySubject.element();
      if (element == null) return optionValues;
      return optionValues.with(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS, JavaSafeDeleteProcessor.searchesInComments(element))
        .with(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE, JavaSafeDeleteProcessor.searchesForText(element));
    }

    @Override
    public void store(@NotNull ModPlanOptionValues answers) {
      PsiElement element = mySubject.element();
      if (element == null) return;
      JavaSafeDeleteProcessor.setSearchesInComments(element, answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_COMMENTS));
      JavaSafeDeleteProcessor.setSearchesForText(element, answers.get(ModPlanSafeDeleteOptions.SEARCH_IN_NON_CODE));
    }
  }
}
