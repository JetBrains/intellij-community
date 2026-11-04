// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.model.SymbolPointer;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanChain;
import com.intellij.refactoring.modplan.ModPlanChainBuilder;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.rename.naming.AutomaticRenamer;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The language side of the rename on the ModPlan engine: it finds what is renamed and the usages, and describes how to edit them. It writes
 * no PSI, shows no UI and opens no modal progress. It gives its questions to the engine, which asks them, as the legacy
 * {@code RenameProcessor} calls the hooks of a processor. Every method says the thread it needs.
 * <p>
 * An edit runs on the copy of one file, and a reference in that copy resolves into the other files as they were before the rename. So a
 * provider decides everything in the analysis, on the physical PSI, and an edit only carries the decision out: it never resolves what it
 * wrote.
 * <p>
 * The owner of a subject is the first provider in the extension order that is applicable to it. A subject that no provider owns, or whose
 * owner is {@linkplain #isLegacyOnly legacy only} for it, is not supported. Only the owner searches and edits a subject. Every provider
 * can take part in the preparation, see {@link #prepare}.
 * <p>
 * <b>The order of the calls.</b> The engine calls the providers in this order, in one run:
 * <ol>
 *   <li>{@link #isOwner} and {@link #isLegacyOnly} find the owner of the requested subject.</li>
 *   <li>{@link #substituteRequest} of the owner decides the target. It can replace the subject, such as by the base method of an
 *   override.</li>
 *   <li>{@link #searchStorage} of the owner of the target. The engine asks the options of the rename for the target and keeps the
 *   answers about the search in the storage.</li>
 *   <li>{@link #prepare} of every provider adds what it renames with the target, the owner first. Each added subject gets its owner.</li>
 *   <li>If the related renames are on: {@link #findUsages} of every subject that a renamer factory is applicable to and that has an
 *   owner, then {@link #renamers} of these subjects with their usages. The engine asks one question for each renamer that has something
 *   to rename. For each chosen element, {@link #prepare} of every provider adds what it renames with it, the owner first.</li>
 *   <li>{@link #findUsages} of every subject that has no usages yet. The engine runs it after the last question. If a
 *   renamer added an element, the engine searches the other subjects again, as a search reads the renamed elements. It also searches a
 *   subject again if PSI changed since its search.</li>
 *   <li>{@link #analyze} of the owner of every subject, in the order of {@link ElementsForRename}. It gives the edits and the conflicts of
 *   the subject. The analysis of the target also gives the conflicts of the new names. A refusal refuses the whole plan at the
 *   subject.</li>
 *   <li>The engine converts the edits into the plan. A place that the user excludes keeps its name: the engine calls {@link #analyze}
 *   again with the usages that are left, with no new search.</li>
 * </ol>
 * <b>A new language.</b>
 * <ol>
 *   <li>Implement this interface and register it as {@code <refactoring.modPlanRenameProvider implementation="..."/>}. The provider of
 *   the headless processors is registered last, so a provider of a language wins over it.</li>
 *   <li>{@link #isOwner}: a cheap check of the element that tells whether the provider owns it, such as its language or its
 *   class.</li>
 *   <li>{@link #substituteRequest}: the substitution. {@link #searchStorage}: where the search options of the target are kept.
 *   {@link #prepare}: what is renamed with a subject, as {@code prepareRenaming}.</li>
 *   <li>{@link #findUsages}: search the usages. The engine runs the search after every question of the run, once for each subject, and
 *   keeps the {@link SubjectUsage}s. {@link #analyze}: the edits and the conflicts of the usages that the
 *   user did not exclude, and for the target also the conflicts of the new names. The engine calls it again after each exclusion, so it
 *   asks nothing and searches nothing. A provider whose code works with the legacy {@code UsageInfo}s extends
 *   {@link ModPlanRenameProviderBase} and overrides its hooks. A hook never returns a chain. The provider asks the user through the
 *   hooks {@code substitutionQuestions} and {@code prepareQuestions}. The base asks these questions, then calls
 *   the hook that computes the result. A reference that needs another form than the new name is a {@link ModPlanWritableUsageInfo} of its own: the
 *   search decides the form, and the usage gives the edits. A non-code usage is a
 *   {@link com.intellij.refactoring.modplan.ModPlanEdit.ReplaceText}.</li>
 *   <li>Keep the options of the language in one class, as {@link ModPlanRenameOptions} keeps the common ones: a headless caller, such as
 *   MCP or LSP, answers the questions with them.</li>
 *   <li>Test the headless run with {@code ModPlanRenameRefactoring.getInstance().plan(request)}, and the questions with
 *   {@code ModPlanTestUtil.runAnswering}.</li>
 * </ol>
 * <b>A migration of a legacy {@link com.intellij.refactoring.rename.RenamePsiElementProcessor}.</b> A processor takes part in the ModPlan
 * rename through a registered provider. While it has none, its elements are refused with {@code NOT_SUPPORTED}: the interactive rename
 * runs the legacy flow, and a headless caller gets the refusal. The exceptions are the default processor and the headless processors,
 * which {@code ModPlanHeadlessRenameProvider} serves.
 * <ul>
 *   <li><b>The processor shows no UI and writes as the default write</b>: nest a subclass of {@link ModPlanBridgeRenameProvider} in the
 *   processor and register it, as {@link com.intellij.refactoring.rename.RenamePsiFileProcessor.ModPlanRenamePsiFileProvider}. Where the
 *   processor differs in one part, the subclass overrides the method of that part, and it can call static helpers of the processor.</li>
 *   <li><b>The processor shows UI or writes in its own way</b>: write a provider of the language. Be applicable only where
 *   {@code RenamePsiElementProcessor.forElement} gives the processor, so that the provider owns the same elements as the legacy rename.
 *   Be legacy only where the provider cannot rename an element that it owns yet. Turn each dialog into a question, and its answer into an
 *   option.</li>
 * </ul>
 */
public interface ModPlanRenameProvider {
  ExtensionPointName<ModPlanRenameProvider> EP_NAME = ExtensionPointName.create("com.intellij.refactoring.modPlanRenameProvider");

  /**
   * Whether this provider owns {@code subject}: a cheap check, no search. The first provider in the extension order that answers true is
   * the owner. As in the legacy rename, where {@code RenamePsiElementProcessor.forElement} gives one processor, a provider is applicable
   * only to the elements that the legacy rename gives to its processor.
   */
  @RequiresReadLock
  boolean isOwner(@NotNull SymbolPointer subject);

  /**
   * Whether only the legacy rename can rename {@code subject}, which this provider owns. True refuses the rename with {@code NOT_SUPPORTED}
   * before any search, and the interactive rename runs the legacy flow. The engine asks no other provider. No search here.
   */
  @ApiStatus.Internal
  @RequiresReadLock
  default boolean isLegacyOnly(@NotNull SymbolPointer subject) {
    return false;
  }

  /**
   * Decides what the run renames, as the legacy rename handler does: {@code substituteElementToRename} with no editor, if
   * {@link ModPlanRenameOptions#SUBSTITUTE_TARGET} is on. The engine then asks the options of the rename dialog for the target, see
   * {@link #searchStorage}. It searches no usages.
   * <p>
   * The engine calls it once, on the owner of the requested subject.
   * <p>
   * A provider that renames the subject itself and asks nothing returns
   * {@code chainBuilder.passValue(new Substitution(subject, newName))}.
   *
   * @return the chain with the questions, which ends with the {@link Substitution}, or with null if the subject cannot be renamed. Null
   * refuses the run.
   */
  @RequiresReadLock
  @NotNull ModPlanChain<@Nullable Substitution> substituteRequest(@NotNull ModPlanChainBuilder chainBuilder,
                                                                  @NotNull SymbolPointer subject,
                                                                  @NotNull String newName);

  /** @return what keeps the search options of {@code target} between the runs, as the rename dialog does; null if nothing keeps them */
  @RequiresReadLock
  default @Nullable ModPlanQuestion.Storage searchStorage(@NotNull SymbolPointer target) {
    return null;
  }

  /**
   * Adds what is renamed with {@code subject}, as {@code prepareRenaming} of the legacy processor. It searches no usages. The engine calls
   * it for the target and for every element that a renamer chose. As the legacy rename calls every processor that can process an element,
   * the engine calls every provider, the owner first. A provider that adds nothing to the rename of {@code subject} returns
   * {@code elementsForRename}.
   *
   * @param elementsForRename everything that is renamed so far, which holds {@code subject} with its new name
   * @return the chain that ends with {@code elementsForRename} and what is renamed with {@code subject}, such as the accessors of a
   * property
   */
  @RequiresReadLock
  default @NotNull ModPlanChain<ElementsForRename> prepare(@NotNull ModPlanChainBuilder chainBuilder,
                                                           @NotNull SymbolPointer subject,
                                                           @NotNull ElementsForRename elementsForRename) {
    return chainBuilder.passValue(elementsForRename);
  }

  /**
   * What {@link #substituteRequest} decides: {@code target} is renamed to {@code newName}. The target is the subject itself, or what replaces
   * it, such as the base method of an override.
   */
  record Substitution(@NotNull SymbolPointer target, @NotNull String newName) {
  }

  /**
   * The renamers of {@code subject}, such as the one of the variables named after a renamed class: every factory of {@code factories} that
   * is applicable to the subject creates one with the usages of the subject, as the legacy rename does. Called for every subject that
   * this provider owns and a factory is applicable to. The engine asks which of their elements are renamed, and adds the chosen ones. It
   * searches nothing.
   *
   * @param elementsForRename everything that is renamed so far
   * @param usages the usages that {@link #findUsages} of this provider found for {@code subject}
   */
  @RequiresReadLock
  default @NotNull @Unmodifiable List<AutomaticRenamer> renamers(@NotNull SymbolPointer subject,
                                                                 @NotNull ElementsForRename elementsForRename,
                                                                 @NotNull List<AutomaticRenamerFactory> factories,
                                                                 @NotNull List<SubjectUsage> usages) {
    return List.of();
  }

  /**
   * A usage of a subject that the search of a provider found. The engine keeps it and gives the kept ones to {@link #analyze}. If two subjects found
   * one place, the engine gives the place only to the first subject, unless the usage is a {@link CollisionKind#RESOLVABLE} collision.
   */
  interface SubjectUsage {
    /** @return the place that a preview shows and the user may exclude; null if the usage is not shown */
    @Nullable SmartPsiFileRange place();

    /** @return the kind of the collision that the usage is; null if it is not a collision */
    default @Nullable CollisionKind collisionKind() {
      return null;
    }

    enum CollisionKind {
      /**
       * An edit resolves the collision, so the usage stays if a usage of an earlier subject has the same place. Another usage does not
       * stay there: two usages at one place are one reference that two subjects found, such as a call that a method and its overrider
       * both find. The engine keeps the first.
       * <p>
       * Example: the field {@code isUTM} and the parameter {@code UTM} both become {@code utm} in {@code isUTM = UTM}. The field finds
       * {@code isUTM} as its reference and writes {@code utm}. The parameter finds the same place as a reference that it hides and writes
       * {@code this.utm}. The plan needs both edits.
       */
      RESOLVABLE,
      /**
       * No edit resolves the collision, so the subject cannot get its new name. Example: the rename of the class {@code D} to {@code A}
       * suggests the rename of the property {@code d} to {@code a}, and the class has a property {@code a} already.
       * <p>
       * The engine reads it for an element that a renamer suggested. It leaves the element out of the run and renames the rest, as the
       * legacy rename does in {@code AutomaticRenamer#findUsages}. For another subject {@link ModPlanRenameProvider#analyze} reports the collision as a
       * conflict.
       */
      UNRESOLVABLE
    }
  }

  /**
   * The search of the usages of {@code subject}, a subject that this provider owns. The engine runs it for a subject that a
   * renamer factory is applicable to before the questions of the related renames, see {@link #renamers}. It runs it for another subject
   * after every question. If a renamer adds an element, the engine searches the subject again with the new
   * {@code elementsForRename}. No PSI writes, no UI, no modal progress, no question. The engine keeps the usages between read actions. If
   * PSI changed since a search, the engine searches again, also for an exclusion.
   * <p>
   * Rule: a question that comes after a search must not change an option that the search reads. No code checks it.
   *
   * @param elementsForRename everything that the run renames so far
   * @param request the request with the answers at the time of the search
   */
  @RequiresReadLock
  @NotNull @Unmodifiable List<SubjectUsage> findUsages(@NotNull SymbolPointer subject,
                                                     @NotNull ElementsForRename elementsForRename,
                                                     @NotNull ModPlanRequest request);

  /**
   * The edits and the conflicts of the rename of {@code subject} and of {@code keptUsages}, as {@code renameElement} gets the usages that
   * are left. It decides again, so an excluded usage changes what the other usages need, such as an import. The engine calls it for the
   * plan and again after each exclusion, so it asks nothing and searches nothing.
   * <p>
   * The analysis of the target, the first subject of {@code elementsForRename}, also gives the conflicts of the new names, as
   * {@code findExistingNameConflicts} of the legacy processor. As in the legacy rename, only the target gets them. They may search, so
   * the analysis runs in the background.
   * <p>
   * The engine gives a place that two subjects found only to the first one, see {@link SubjectUsage}. The edits of all subjects run in one
   * copy of each file. A reference is written at its range, see {@link ModPlanRenameUtil#referenceEdits}, so the write of an earlier
   * subject can move the reference or build its element again.
   *
   * @param elementsForRename everything that the run renames
   * @param keptUsages the usages of {@link #findUsages} for {@code subject} that the user did not exclude
   */
  @RequiresReadLock
  @RequiresBackgroundThread
  @NotNull ModPlanAnalysisResult analyze(@NotNull SymbolPointer subject,
                                         @NotNull ElementsForRename elementsForRename,
                                         @NotNull List<SubjectUsage> keptUsages);

  /**
   * The subjects that a run renames, with their new names, in the order that the engine found them: the requested subject, or what is
   * renamed instead of it, first. It is immutable. A {@link SymbolPointer} is compared by identity, so this class compares the symbols,
   * and a subject is in it once.
   */
  final class ElementsForRename {
    public static final ElementsForRename EMPTY = new ElementsForRename(Map.of());

    private final @NotNull @Unmodifiable Map<SymbolPointer, String> myNewNames;

    private ElementsForRename(@NotNull Map<SymbolPointer, String> newNames) {
      myNewNames = Collections.unmodifiableMap(newNames);
    }

    public static @NotNull ElementsForRename of(@NotNull SymbolPointer subject, @NotNull String newName) {
      return new ElementsForRename(Map.of(subject, newName));
    }

    /** @return these new names, then the ones of {@code added} for the subjects that have none yet */
    @RequiresReadLock
    public @NotNull ElementsForRename plus(@NotNull ElementsForRename added) {
      Map<SymbolPointer, String> result = new LinkedHashMap<>(myNewNames);
      added.myNewNames.forEach((subject, newName) -> {
        if (keyOf(subject) == null) {
          result.put(subject, newName);
        }
      });
      return new ElementsForRename(result);
    }

    @RequiresReadLock
    public @NotNull ElementsForRename without(@NotNull SymbolPointer subject) {
      Map<SymbolPointer, String> result = new LinkedHashMap<>(myNewNames);
      result.remove(keyOf(subject));
      return new ElementsForRename(result);
    }

    public @NotNull @Unmodifiable List<SymbolPointer> subjects() {
      return List.copyOf(myNewNames.keySet());
    }

    /** @return the new name of {@code subject}; null if it is not renamed */
    @RequiresReadLock
    public @Nullable String newNameOf(@NotNull SymbolPointer subject) {
      SymbolPointer key = keyOf(subject);
      return key == null ? null : myNewNames.get(key);
    }

    /** @return the renamed elements with their new names, in order; null if one is gone */
    @RequiresReadLock
    public @Nullable Map<PsiElement, String> elements() {
      Map<PsiElement, String> result = new LinkedHashMap<>();
      for (Map.Entry<SymbolPointer, String> entry : myNewNames.entrySet()) {
        PsiElement element = entry.getKey().element();
        if (element == null) return null;
        result.put(element, entry.getValue());
      }
      return result;
    }

    private @Nullable SymbolPointer keyOf(@NotNull SymbolPointer subject) {
      for (SymbolPointer key : myNewNames.keySet()) {
        if (key == subject || key.isSameSymbol(subject)) return key;
      }
      return null;
    }
  }
}
