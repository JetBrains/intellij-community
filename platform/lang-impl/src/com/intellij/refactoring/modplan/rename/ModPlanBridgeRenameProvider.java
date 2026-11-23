// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.psi.PsiElement;
import com.intellij.psi.meta.PsiMetaOwner;
import com.intellij.psi.meta.PsiWritableMetaData;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.rename.RenamePsiElementProcessor;
import com.intellij.refactoring.rename.RenamePsiElementProcessorBase;
import com.intellij.refactoring.rename.RenamePsiElementProcessorCore;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Runs a plain legacy {@link RenamePsiElementProcessor} on the ModPlan engine, by composition: the search and the preparation are the legacy
 * ones, and the edits are the ones of the default write of a rename, see {@link ModPlanRenameProviderBase#analyze}. It serves the
 * processors that have no provider of their own, such as the one of the files. The providers of Java and Kotlin extend it.
 * <p>
 * The processor must not ask the user in {@code substituteElementToRename}
 * with no editor, {@code prepareRenaming}, {@code findReferences}, {@code findCollisions} and {@code findExistingNameConflicts}, and must
 * write as the default write: it declares no {@code renameElement} and no {@code getPostRenameCallback}. A processor that differs
 * overrides the methods of this class that hold the difference, or has a provider of its own.
 * <p>
 * The provider owns an element if the legacy rename gives it to a processor of exactly the registered class: a subclass of the processor
 * is another processor, and it is registered on its own.
 * <p>
 * <b>Refusals:</b> an element whose metadata writes its name, and a usage whose reference binds to the renamed element after the write,
 * see {@link ModPlanRenameUtil#isRenamedByName}.
 * <p>
 * <b>To delete the processor of a provider</b>, override the hooks that reach it, each with the static helper that the processor
 * method calls: {@link #isOwner} with the check of {@code canProcessElement}; {@link #core} with an object that implements
 * {@code RenamePsiElementProcessorCore} by the same helpers; {@link #prepareRenaming}, {@link #nameConflicts},
 * {@link #searchStorage} and {@link #substitute}. Then nothing in the provider names the processor.
 */
@ApiStatus.Experimental
public class ModPlanBridgeRenameProvider extends ModPlanRenameProviderBase {
  private final @NotNull Class<?> myProcessorClass;

  /**
   * @param processorClass the class of the legacy processor that this provider runs, see {@link #isOwner}
   */
  protected ModPlanBridgeRenameProvider(@NotNull Class<?> processorClass) {
    myProcessorClass = processorClass;
  }

  /** The class of the legacy processor that this provider runs. */
  @ApiStatus.Internal
  public final @NotNull Class<?> processorClass() {
    return myProcessorClass;
  }

  /** As {@code substituteElementToRename} of the processor that the legacy rename gives {@code element} to, with no editor. */
  @Override
  protected @Nullable PsiElement substitute(@NotNull PsiElement element, @NotNull ModPlanOptionValues answers) {
    return RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, null);
  }

  /** As {@code prepareRenaming} of the processor that the legacy rename gives {@code element} to. */
  @Override
  protected void prepareRenaming(@NotNull PsiElement element,
                                 @NotNull String newName,
                                 @NotNull Map<PsiElement, String> renamed,
                                 @NotNull ModPlanOptionValues answers) {
    RenamePsiElementProcessor.forElement(element).prepareRenaming(element, newName, renamed);
  }

  /** @return what searches the usages of {@code element} */
  protected @NotNull RenamePsiElementProcessorCore core(@NotNull PsiElement element) {
    return RenamePsiElementProcessor.forElement(element);
  }

  /** The legacy rename gives {@code subject} to a processor of the class of this provider. */
  @Override
  public boolean isOwner(@NotNull SymbolPointer subject) {
    PsiElement element = subject.element();
    return element != null && RenamePsiElementProcessor.forElement(element).getClass() == myProcessorClass;
  }

  /** Refuses an element whose metadata writes its name. */
  @Override
  protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                   @NotNull String newName,
                                                   @NotNull List<UsageInfo> usages,
                                                   @NotNull Map<PsiElement, String> renamed) {
    if (element instanceof PsiMetaOwner owner && owner.getMetaData() instanceof PsiWritableMetaData) {
      return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_SUPPORTED);
    }
    return super.analyze(element, newName, usages, renamed);
  }

  /** The conflicts of the new names, as {@code findExistingNameConflicts} finds them. */
  @Override
  protected @NotNull MultiMap<PsiElement, @DialogMessage String> nameConflicts(@NotNull PsiElement element,
                                                                              @NotNull String newName,
                                                                              @NotNull Map<PsiElement, String> renamed) {
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    RenamePsiElementProcessor.forElement(element).findExistingNameConflicts(element, newName, conflicts, renamed);
    return conflicts;
  }

  @Override
  protected @NotNull List<UsageInfo> usagesOf(@NotNull PsiElement element,
                                            @NotNull String newName,
                                            @NotNull ModPlanRequest request,
                                            @NotNull Map<PsiElement, String> renamed) {
    ModPlanOptionValues optionValues = request.optionValues();
    UsageInfo[] usages = RenameUtil.findUsages(element, newName, request.searchScope().get(),
                                               optionValues.get(ModPlanRenameOptions.SEARCH_IN_COMMENTS),
                                               optionValues.get(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES), renamed,
                                               core(element));
    return ModPlanRenameUtil.ownUsages(element, renamed, List.of(usages));
  }

  @Override
  public @Nullable ModPlanQuestion.Storage searchStorage(@NotNull SymbolPointer target) {
    PsiElement element = target.element();
    return element == null ? null : new SearchStorage(target, RenamePsiElementProcessor.forElement(element));
  }

  /** Keeps the search options in the settings that the processor uses for the kind of the element, as the rename dialog does. */
  @ApiStatus.Internal
  public record SearchStorage(@NotNull SymbolPointer subject, @NotNull RenamePsiElementProcessorBase processor)
    implements ModPlanQuestion.Storage {
    @Override
    public @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues options) {
      PsiElement element = subject.element();
      if (element == null) return options;
      return options.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, processor.isToSearchInComments(element))
        .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, processor.isToSearchForTextOccurrences(element));
    }

    @Override
    public void store(@NotNull ModPlanOptionValues answers) {
      PsiElement element = subject.element();
      if (element == null) return;
      processor.setToSearchInComments(element, answers.get(ModPlanRenameOptions.SEARCH_IN_COMMENTS));
      processor.setToSearchForTextOccurrences(element, answers.get(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES));
    }
  }
}
