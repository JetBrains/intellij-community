// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.safeDelete;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanFinalStep;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.safeDelete.SafeDeleteProcessor;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;
import java.util.List;

/**
 * The language side of the safe delete on the ModPlan engine: it finds the usages of a subject and describes how to edit them. It writes
 * no PSI, shows no UI and opens no modal progress. The engine calls {@link #prepareRequest} for every subject, then {@link #analyze} for
 * every deleted one, see {@link com.intellij.refactoring.modplan.safeDelete}. Read the options from {@code chainBuilder.getRequest()}: only
 * it holds the answers so far.
 * <p>
 * <b>A new language.</b>
 * <ol>
 *   <li>Implement this interface and register it as {@code <refactoring.modPlanSafeDeleteProvider implementation="..."/>}. The bridge of
 *   the legacy delegates is registered last, so a provider of a language wins over it.</li>
 *   <li>{@link #isApplicable}: a cheap check of the element, such as its language or its class.</li>
 *   <li>{@link #prepareRequest}: return {@code chainBuilder.passValue(DeletionDecision.DELETED)} for a subject that goes alone or that is not
 *   requested, and {@link DeletionDecision#deletedWith} for the elements that go with it, such as
 *   the accessors of a field. Ask a question with {@link ModPlanChainBuilder#ask} where the answer changes what is deleted.</li>
 *   <li>{@link #analyze}: search the usages with {@code deletedSubjects} and return {@code chainBuilder.finish(request -> new
 *   ModPlanAnalysis.Found(edits, conflicts))}, with the search inside the step, so that it runs after every question of the run. A
 *   provider whose code works with the legacy {@code UsageInfo}s can use {@link ModPlanSafeDeleteUsageInfoUtil}.</li>
 *   <li>Each question is a {@link com.intellij.refactoring.modplan.ModPlanQuestion.Form} of options, or a
 *   {@link com.intellij.refactoring.modplan.ModPlanQuestion.Custom} with its own dialog. Keep the options of the language in one class,
 *   such as {@code ModPlanJavaSafeDeleteOptions}: a headless caller, such as MCP or LSP, answers the questions with them.</li>
 *   <li>Test the headless run with {@code ModPlanSafeDeleteRefactoring.getInstance().plan(request)}, and the questions with
 *   {@code ModPlanTestUtil.runAnswering}.</li>
 * </ol>
 * <b>A migration of a legacy {@link com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate}.</b> A delegate takes part in the
 * ModPlan safe delete through {@link com.intellij.refactoring.safeDelete.SafeDeleteProcessorDelegate#asModPlan}. While it returns null, the
 * subjects of the delegate are refused with {@code NOT_SUPPORTED}: the interactive safe delete runs the legacy flow, and a headless caller
 * gets the refusal.
 * <ul>
 *   <li><b>The delegate shows no UI</b> in {@code getElementsToSearch}, {@code findConflicts}, {@code preprocessUsages} and
 *   {@code prepareForDeletion}: return {@code new ModPlanLegacySafeDeleteProvider(this)} from {@code asModPlan}. Nothing else is needed:
 *   the adapter runs the legacy code and answers a question of the legacy code with its default. See
 *   {@code PropertyKeysSafeDeleteProcessor}.</li>
 *   <li><b>The delegate shows UI</b>: write a provider of the language, as {@code ModPlanJavaSafeDeleteProvider} and
 *   {@code ModPlanKotlinSafeDeleteProvider} do.
 *   <ul>
 *     <li>In {@link #isApplicable}, own an element when {@link ModPlanLegacySafeDeleteProvider#legacyHandlerOf} is the delegate itself, so
 *     that the provider owns the same elements as the legacy flow, and a subclass of the delegate stays on the legacy flow.</li>
 *     <li>Turn each dialog into a question, and its answer into an option.</li>
 *     <li>Write a step with a question as a helper that takes the builder first and returns the chain that ends with its value, such as
 *     {@code JavaSafeDeleteProcessor.elementsToSearch}. The provider goes on with the value in {@link ModPlanChain#andThen}. The legacy
 *     method of the delegate runs the same helper through
 *     {@link com.intellij.refactoring.modplan.ModPlanUiInterpreter}, so that both flows share one code and ask the same dialogs.</li>
 *   </ul></li>
 * </ul>
 */
public interface ModPlanSafeDeleteProvider {
  ExtensionPointName<ModPlanSafeDeleteProvider> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.modPlanSafeDeleteProvider");

  /**
   * Cheap ownership check, no search. The first provider in the extension order that answers true owns the subject; a named element or a
   * file that no provider owns goes to the generic provider, where every reference is a conflict.
   */
  @RequiresReadLock
  boolean isApplicable(@NotNull SymbolPointer subject);

  /**
   * Whether only the legacy safe delete can delete {@code subject}, which this provider owns. True refuses the deletion with
   * {@code NOT_SUPPORTED} before any search, and the interactive safe delete runs the legacy flow. No search here.
   */
  @ApiStatus.Internal
  @RequiresReadLock
  default boolean isLegacyOnly(@NotNull SymbolPointer subject) {
    return false;
  }

  /**
   * Decides whether {@code subject} can be deleted and what is deleted with it. Called once for every subject of a run, the requested ones
   * and the ones that they add. It searches no usages.
   * <p>
   * For a requested subject the chain asks the questions whose answers change what is deleted, such as the base methods to delete too, and
   * the search options. The chain ends with a {@link DeletionDecision}: {@link DeletionDecision#REFUSED}, {@link DeletionDecision#DELETED},
   * {@link DeletionDecision#deletedWith} the subjects to delete too, which may contain a file, or {@link DeletionDecision#replacedBy} the
   * subjects to delete instead of this one. With {@link ModPlanSafeDeleteOptions#SEARCH_RELATED}
   * false it adds only what must go with the subject anyway, such as the additional elements it asks about, and not the related ones,
   * such as the base methods or the whole hierarchy of a parameter. For a subject that is not requested, return
   * {@link DeletionDecision#DELETED}: an added subject asks nothing and adds nothing, as in the legacy safe delete.
   * <p>
   * <b>A requested subject</b> is one that {@code chainBuilder.getRequest().subjects()} contains. Check it with {@code contains}: the engine
   * passes the instance of the request for a requested subject and a new instance for an added one, and {@link SymbolPointer} is compared
   * by identity. Do not compare with {@link SymbolPointer#isSameSymbol} and do not create a pointer for the check: an added subject of the
   * same symbol as a requested one is never passed, the engine keeps the requested instance.
   *
   * @param chainBuilder    holds the request of the caller with the answers so far
   * @param subject         a subject that this provider owns, requested or added
   * @param deletedSubjects the subjects that are deleted so far: the requested ones and the ones that the subjects before this one added.
   *                        A question about an element that is deleted anyway is not asked, such as a base method that an earlier subject
   *                        added
   * @return the chain with the questions of the subject, which ends with its {@link DeletionDecision}: {@code chainBuilder.passValue(...)}
   * if nothing is asked, {@code chainBuilder.ask(question, answered -> answered.passValue(...))} otherwise
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull ModPlanChain<DeletionDecision> prepareRequest(@NotNull ModPlanChainBuilder chainBuilder,
                                                    @NotNull SymbolPointer subject,
                                                    @NotNull @Unmodifiable List<SymbolPointer> deletedSubjects);

  /**
   * Finds the usages of {@code subject} and gives the edits and the conflicts of its deletion, non-code usages included. Called for every
   * deleted subject, after every {@link #prepareRequest}. The chain can ask about the usages, such as which overriding methods to delete.
   * No PSI writes, no UI, no modal progress. A usage that still needs the subject is a broken conflict; its text is
   * {@link SafeDeleteProcessor#unsafeUsageMessage(PsiElement, PsiElement)}, as in the interactive safe delete.
   * <p>
   * End the chain with {@link ModPlanChainBuilder#finish}: the engine runs its step at the end of the run, after the questions of every
   * subject. Put into the step all that needs no answer: the search, if no question needs it, the conflicts and the edits. The
   * step captures only smart pointers and subjects, see {@link ModPlanFinalStep}.
   *
   * @param chainBuilder    holds the request of the caller with the answers so far
   * @param subject         a subject that this provider owns
   * @param deletedSubjects everything that is deleted: the requested subjects and the ones that they add. A usage inside one of them is
   *                        deleted with it, so it is not a conflict
   * @return the chain with the questions about the usages, which ends with the step of the analysis of the subject, see
   * {@link ModPlanChainBuilder#finish}: {@link ModPlanAnalysisResult.Found} with the edits of the usages and the conflicts, or
   * {@link ModPlanAnalysisResult.Refused} if the subject cannot be deleted, which refuses the whole run. Each path of the chain ends with one
   * {@code finish}
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull ModPlanChain<ModPlanFinalStep<ModPlanAnalysisResult>> analyze(
    @NotNull ModPlanChainBuilder chainBuilder,
    @NotNull SymbolPointer subject,
    @NotNull @Unmodifiable List<SymbolPointer> deletedSubjects
  );

  /**
   * What {@link #prepareRequest} decides for a subject. The engine adds the subjects of the decision to the deleted subjects and starts each
   * new one with {@link #prepareRequest}; a refused subject refuses the whole run.
   */
  sealed interface DeletionDecision {
    /** The subject cannot be deleted, such as a subject with nothing to search, so the run is refused. */
    DeletionDecision REFUSED = new Refused();
    /** The subject is deleted, and nothing goes with it. */
    DeletionDecision DELETED = new Deleted(List.of());

    /** @return the subject is deleted, and {@code alsoDeleted} goes with it, such as the accessors of a field */
    static @NotNull DeletionDecision deletedWith(@NotNull Collection<SymbolPointer> alsoDeleted) {
      return new Deleted(List.copyOf(alsoDeleted));
    }

    /** @return the subject stays, and {@code replacements} are deleted instead of it, such as the base declarations of a Kotlin override */
    static @NotNull DeletionDecision replacedBy(@NotNull Collection<SymbolPointer> replacements) {
      return new Replaced(List.copyOf(replacements));
    }

    /** The subject cannot be deleted, so the run is refused. */
    record Refused() implements DeletionDecision {
    }

    /** @param alsoDeleted what is deleted with the subject */
    record Deleted(@NotNull @Unmodifiable List<SymbolPointer> alsoDeleted) implements DeletionDecision {
      public Deleted {
        alsoDeleted = List.copyOf(alsoDeleted);
      }
    }

    /**
     * The subject stays: the engine neither deletes nor analyzes it, and the analysis of its replacements edits it as a usage.
     *
     * @param replacements what is deleted instead of the subject
     */
    record Replaced(@NotNull @Unmodifiable List<SymbolPointer> replacements) implements DeletionDecision {
      public Replaced {
        replacements = List.copyOf(replacements);
      }
    }
  }
}
