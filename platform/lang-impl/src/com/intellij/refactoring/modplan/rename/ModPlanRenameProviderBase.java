// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.SmartPointersKt;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.rename.CollisionUsageInfo;
import com.intellij.refactoring.rename.RenameUtilBase;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.NonCodeUsageInfo;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The steps of a {@link ModPlanRenameProvider} that the providers that work with the legacy {@link UsageInfo}s share. A subclass gives the
 * hooks: what is prepared, how the usages are found, and what the declaration gets.
 * <p>
 * A subclass must override {@link #usagesOf}. It may override {@link #nameConflicts(PsiElement, String, Map)},
 * {@link #substitutionQuestions}, {@link #substitute}, {@link #searchStorage}, {@link #shouldPrepareWith}, {@link #prepareQuestions},
 * {@link #prepareRenaming}, {@link #analyze(PsiElement, String, List, Map)}, {@link #referenceEdits} and {@link #declarationEdits}.
 * <p>
 * A hook is synchronous and never returns a chain. A subclass asks the user only through {@link #substitutionQuestions} and
 * {@link #prepareQuestions}. The base asks these questions, then calls the hook that computes the result. The engine asks the
 * options that {@link #searchStorage} keeps.
 */
@ApiStatus.Experimental
public abstract class ModPlanRenameProviderBase implements ModPlanRenameProvider {
  /**
   * As the legacy rename handler: the target is the value of {@link #substitute} after {@link #substitutionQuestions} if
   * {@link ModPlanRenameOptions#SUBSTITUTE_TARGET} is on, else the subject.
   */
  @Override
  public @NotNull ModPlanChain<@Nullable Substitution> substituteRequest(@NotNull ModPlanChainBuilder chainBuilder,
                                                                         @NotNull SymbolPointer subject,
                                                                         @NotNull String newName) {
    PsiElement element = subject.element();
    if (element == null) return chainBuilder.passValue(null);
    boolean substitutes = chainBuilder.getRequest().optionValues().get(ModPlanRenameOptions.SUBSTITUTE_TARGET);
    List<ModPlanQuestion> substitutionQuestions = substitutes ? substitutionQuestions(subject, element) : List.of();
    return chainBuilder.askAll(substitutionQuestions, afterSubstitution -> {
      PsiElement current = subject.element();
      if (current == null) return afterSubstitution.passValue(null);
      PsiElement renamed = substitutes ? substitute(current, afterSubstitution.getRequest().optionValues()) : current;
      if (renamed == null) return afterSubstitution.passValue(null);
      SymbolPointer target = renamed == current ? subject : SymbolPointer.of(renamed);
      return afterSubstitution.passValue(new Substitution(target, newName));
    });
  }

  /** @return the questions to ask before {@link #substitute}, such as the one about the base method of an override */
  @RequiresReadLock
  protected @NotNull @Unmodifiable List<ModPlanQuestion> substitutionQuestions(@NotNull SymbolPointer subject, @NotNull PsiElement element) {
    return List.of();
  }

  /**
   * @param answers the answers of the request, with the ones of {@link #substitutionQuestions}
   * @return the element to rename instead of {@code element}, as {@code substituteElementToRename} of the processor with no editor;
   * null refuses the rename
   */
  @RequiresReadLock
  protected @Nullable PsiElement substitute(@NotNull PsiElement element, @NotNull ModPlanOptionValues answers) {
    return element;
  }

  /** @return what keeps the search options of {@code target} between the runs, as the rename dialog does; null if nothing keeps them */
  @Override
  @RequiresReadLock
  public @Nullable ModPlanQuestion.Storage searchStorage(@NotNull SymbolPointer target) {
    return null;
  }

  @Override
  public @NotNull ModPlanChain<ElementsForRename> prepare(@NotNull ModPlanChainBuilder chainBuilder,
                                                          @NotNull SymbolPointer subject,
                                                          @NotNull ElementsForRename elementsForRename) {
    PsiElement element = subject.element();
    String newName = elementsForRename.newNameOf(subject);
    if (element == null || newName == null || !shouldPrepareWith(subject)) return chainBuilder.passValue(elementsForRename);
    return chainBuilder.askAll(prepareQuestions(element, newName), afterQuestions -> {
      PsiElement current = subject.element();
      if (current == null) return afterQuestions.passValue(elementsForRename);
      ModPlanOptionValues answers = afterQuestions.getRequest().optionValues();
      Map<PsiElement, String> renamed = new LinkedHashMap<>();
      renamed.put(current, newName);
      prepareRenaming(current, newName, renamed, answers);
      ElementsForRename result = elementsForRename;
      for (Map.Entry<PsiElement, String> entry : renamed.entrySet()) {
        if (entry.getKey() != current) {
          result = result.plus(ElementsForRename.of(SymbolPointer.of(entry.getKey()), entry.getValue()));
        }
      }
      return afterQuestions.passValue(result);
    });
  }

  /**
   * @return whether {@link #prepareRenaming} adds to the rename of {@code subject}, as the legacy rename lets every processor that can
   * process an element add to its rename; by default, if this provider owns it
   */
  @RequiresReadLock
  protected boolean shouldPrepareWith(@NotNull SymbolPointer subject) {
    return isOwner(subject);
  }

  /** @return the questions to ask before {@link #prepareRenaming}, as the dialogs of {@code prepareRenaming} of the processor */
  @RequiresReadLock
  protected @NotNull @Unmodifiable List<ModPlanQuestion> prepareQuestions(@NotNull PsiElement element, @NotNull String newName) {
    return List.of();
  }

  /**
   * Adds what {@code prepareRenaming} of the processor renames with {@code element} to {@code renamed}.
   *
   * @param answers the answers of the request, with the ones of {@link #prepareQuestions}
   */
  protected void prepareRenaming(@NotNull PsiElement element,
                                 @NotNull String newName,
                                 @NotNull Map<PsiElement, String> renamed,
                                 @NotNull ModPlanOptionValues answers) {
  }

  /** The renamers get the usages of {@code usagesOf} that {@link #findUsages} found. */
  @Override
  public @NotNull List<AutomaticRenamer> renamers(@NotNull SymbolPointer subject,
                                                  @NotNull ElementsForRename elementsForRename,
                                                  @NotNull List<AutomaticRenamerFactory> factories,
                                                  @NotNull List<SubjectUsage> usages) {
    List<UsageInfo> usageInfos = new ArrayList<>();
    for (SubjectUsage usage : usages) {
      if (usage instanceof FoundUsage found) {
        usageInfos.add(found.usage());
      }
    }
    PsiElement element = subject.element();
    String newName = elementsForRename.newNameOf(subject);
    if (element == null || newName == null) return List.of();
    List<AutomaticRenamerFactory> applicableFactories = ContainerUtil.filter(factories, factory -> factory.isApplicable(element));
    return ContainerUtil.map(applicableFactories, factory -> factory.createRenamer(element, newName, usageInfos));
  }

  /** @return the usages of {@code element}, as the legacy rename finds them with the processor, see {@link ModPlanRenameUtil#ownUsages} */
  protected abstract @NotNull List<UsageInfo> usagesOf(@NotNull PsiElement element,
                                                       @NotNull String newName,
                                                       @NotNull ModPlanRequest request,
                                                       @NotNull Map<PsiElement, String> renamed);

  /**
   * @return the conflicts of the new names that the legacy rename finds for the element it starts from: the ones of {@code element}, the
   * target. {@link #analyze(SymbolPointer, ElementsForRename, List)} adds them as broken conflicts. By default, none.
   */
  @RequiresBackgroundThread
  protected @NotNull MultiMap<PsiElement, @DialogMessage String> nameConflicts(@NotNull PsiElement element,
                                                                              @NotNull String newName,
                                                                              @NotNull Map<PsiElement, String> renamed) {
    return new MultiMap<>();
  }

  /** The search of the usages of a subject: every usage of {@code usagesOf} with its place. */
  @Override
  public @NotNull @Unmodifiable List<SubjectUsage> findUsages(@NotNull SymbolPointer subject,
                                                            @NotNull ElementsForRename elementsForRename,
                                                            @NotNull ModPlanRequest request) {
    PsiElement element = subject.element();
    String newName = elementsForRename.newNameOf(subject);
    Map<PsiElement, String> renamed = elementsForRename.elements();
    if (element == null || newName == null || renamed == null) return List.of();
    List<SubjectUsage> result = new ArrayList<>();
    for (UsageInfo usage : usagesOf(element, newName, request, renamed)) {
      PsiElement usageElement = usage.getElement();
      boolean isShown = usageElement != null && !(usage instanceof UnresolvableCollisionUsageInfo) &&
                        (usage instanceof NonCodeUsageInfo || usage instanceof ModPlanWritableUsageInfo || !renamed.containsKey(usageElement));
      result.add(new FoundUsage(usage, isShown ? SmartPointersKt.createSmartFileRange(usageElement, usage.getRangeInElement()) : null));
    }
    return result;
  }

  /**
   * The edits of {@link #analyze(PsiElement, String, List, Map)}. The target also gets the conflicts of
   * {@link #nameConflicts(PsiElement, String, Map)}. A usage that {@link #findUsages} of this class did not find refuses the plan.
   */
  @Override
  @RequiresBackgroundThread
  public @NotNull ModPlanAnalysisResult analyze(@NotNull SymbolPointer subject,
                                                @NotNull ElementsForRename elementsForRename,
                                                @NotNull List<SubjectUsage> keptUsages) {
    PsiElement element = subject.element();
    String newName = elementsForRename.newNameOf(subject);
    Map<PsiElement, String> renamed = elementsForRename.elements();
    if (element == null || newName == null || renamed == null) {
      return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
    }
    List<UsageInfo> usages = new ArrayList<>();
    for (SubjectUsage usage : keptUsages) {
      if (!(usage instanceof FoundUsage found)) return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED);
      usages.add(found.usage());
    }
    ModPlanAnalysisResult result = analyze(element, newName, usages, renamed);
    if (!(result instanceof ModPlanAnalysisResult.Found(List<ModPlanEdit> edits, ModPlanConflicts conflicts)) ||
        !subject.isSameSymbol(elementsForRename.subjects().getFirst())) {
      return result;
    }
    ModPlanConflicts.Builder conflictsOfNames = ModPlanConflicts.builder();
    for (Map.Entry<PsiElement, Collection<@DialogMessage String>> entry : nameConflicts(element, newName, renamed).entrySet()) {
      for (@DialogMessage String message : entry.getValue()) {
        conflictsOfNames.broken(entry.getKey(), message);
      }
    }
    return new ModPlanAnalysisResult.Found(edits, conflictsOfNames.build().merge(conflicts));
  }

  /**
   * The edits of the rename of {@code element} and of its {@code usages}, as {@code renameElement} of the processor writes them: the
   * edits of the references, then of the declaration, then of each {@link ModPlanWritableUsageInfo}, as the legacy rename resolves the
   * collisions after the rename. A non-code usage gets its new text after every PSI edit. A collision that cannot be resolved is a broken
   * conflict. {@code usages} are the ones that the user did not exclude.
   *
   * @return the edits and the conflicts; a {@code NOT_SUPPORTED} refusal if a reference cannot get the new name
   */
  @RequiresReadLock
  protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull List<UsageInfo> usages,
                                                   @NotNull Map<PsiElement, String> renamed) {
    List<ModPlanEdit> referenceEdits = referenceEdits(element, newName, referencesAmong(usages, renamed), renamed);
    if (referenceEdits == null) return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED);
    List<ModPlanEdit> edits = new ArrayList<>(referenceEdits);
    edits.addAll(declarationEdits(element, newName));
    ModPlanConflicts.Builder conflicts = ModPlanConflicts.builder();
    for (UsageInfo usage : usages) {
      PsiElement usageElement = usage.getElement();
      if (usageElement == null) continue;
      if (usage instanceof ModPlanWritableUsageInfo editable) {
        List<ModPlanEdit> usageEdits = editable.asModPlanEdits(newName, renamed);
        if (usageEdits == null) {
          conflicts.broken(usageElement, RefactoringBundle.message("rename.usage.unmodifiable"));
        }
        else {
          edits.addAll(usageEdits);
        }
      }
    }
    for (UsageInfo usage : usages) {
      PsiElement usageElement = usage.getElement();
      if (usageElement == null) continue;
      if (usage instanceof NonCodeUsageInfo nonCode) {
        SmartPsiFileRange place = SmartPointersKt.createSmartFileRange(usageElement, nonCode.getRangeInElement());
        if (place != null) edits.add(new ModPlanEdit.ReplaceText(place, nonCode.newText));
        conflicts.changedNonCodeUsage(usageElement, nonCode.getRangeInElement());
      }
      else if (usage instanceof UnresolvableCollisionUsageInfo collision) {
        conflicts.broken(usageElement, collision.getDescription());
      }
    }
    return new ModPlanAnalysisResult.Found(edits, conflicts.build());
  }

  /**
   * @return the edits of {@code references}, the references among the usages, see {@link #referencesAmong}: by default each one gets
   * the new name, see {@link ModPlanRenameUtil#referenceEdits}; null to refuse the plan with {@code NOT_SUPPORTED}
   */
  @RequiresReadLock
  protected @Nullable List<ModPlanEdit> referenceEdits(@NotNull PsiElement element,
                                                       @NotNull String newName,
                                                       @NotNull List<UsageInfo> references,
                                                       @NotNull Map<PsiElement, String> renamed) {
    return ModPlanRenameUtil.referenceEdits(references, newName);
  }

  /**
   * @return the usages of {@code usages} that refer to the renamed element: no non-code usage, no collision that cannot be resolved, no
   * {@link ModPlanWritableUsageInfo}, no renamed element. Every other usage is a reference, as {@link RenameUtilBase#rename} writes it
   */
  @RequiresReadLock
  protected static @NotNull List<UsageInfo> referencesAmong(@NotNull List<UsageInfo> usages, @NotNull Map<PsiElement, String> renamed) {
    List<UsageInfo> result = new ArrayList<>();
    for (UsageInfo usage : usages) {
      PsiElement usageElement = usage.getElement();
      if (usageElement != null && !(usage instanceof NonCodeUsageInfo) && !(usage instanceof UnresolvableCollisionUsageInfo) &&
          !(usage instanceof ModPlanWritableUsageInfo) && !renamed.containsKey(usageElement)) {
        result.add(usage);
      }
    }
    return result;
  }

  /**
   * @return the edits that rename the declaration of {@code element}. An element with no text of its own gets no edit, and its usages
   * get the new name. Such an element is a fake element of no file, such as a path variable. It is also a light element whose source has
   * another name, such as a Lombok getter. The subject of the source renames the source.
   */
  protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
    if (element instanceof PomTargetPsiElement && element.getContainingFile() == null) return List.of();
    PsiElement source = element.getNavigationElement();
    if (!element.isPhysical() && source != element && source instanceof PsiNamedElement namedSource &&
        element instanceof PsiNamedElement named && !Objects.equals(namedSource.getName(), named.getName())) {
      return List.of();
    }
    return List.of(ModPlanRenameUtil.declarationEdit(element, newName));
  }

  /** A usage of {@code usagesOf} with its place, see {@link #findUsages}. */
  private record FoundUsage(@NotNull UsageInfo usage, @Nullable SmartPsiFileRange place) implements SubjectUsage {
    @Override
    public @Nullable CollisionKind collisionKind() {
      if (usage instanceof UnresolvableCollisionUsageInfo) return CollisionKind.UNRESOLVABLE;
      return usage instanceof CollisionUsageInfo ? CollisionKind.RESOLVABLE : null;
    }
  }
}
