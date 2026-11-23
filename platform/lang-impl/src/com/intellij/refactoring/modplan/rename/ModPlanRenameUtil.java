// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.modplan.rename;

import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.SmartPsiFileRange;
import com.intellij.psi.impl.source.resolve.reference.impl.PsiMultiReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReference;
import com.intellij.psi.impl.source.resolve.reference.impl.providers.FileReferenceSet;
import com.intellij.refactoring.RefactoringSettings;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOption;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.rename.BindablePsiReference;
import com.intellij.refactoring.rename.FragmentaryPsiReference;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.UnresolvableCollisionUsageInfo;
import com.intellij.refactoring.rename.naming.AutomaticRenamerFactory;
import com.intellij.refactoring.util.TextOccurrencesUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static helpers for the providers that rename with the legacy {@code UsageInfo}s, which the search of the legacy rename finds, see
 * {@link #ownUsages}. Every edit here carries out a decision of the analysis and resolves nothing in the copy it writes. The one
 * exception is the bind of a file reference, which computes the path to its target, see {@link #referenceEdits}.
 */
@ApiStatus.Internal
public final class ModPlanRenameUtil {
  private ModPlanRenameUtil() {
  }

  /**
   * @param searchStorage keeps the answers about the search in comments and in text occurrences, as the rename dialog does; null if no
   *                      setting keeps them
   * @return the standard questions of the rename of {@code target}, as the rename dialog shows them: one form of the search in comments,
   * and in text occurrences where they can be searched; then, if a factory renames something with the target, the general switch of the related
   * renames and one switch for the factories of one option, kept in their settings. Which elements a renamer renames, the engine asks
   * later, with one {@code RelatedRenamesQuestion} for each renamer
   */
  @RequiresReadLock
  public static @NotNull @Unmodifiable List<ModPlanQuestion> renameQuestions(@NotNull PsiElement target,
                                                                            @Nullable ModPlanQuestion.Storage searchStorage) {
    List<ModPlanQuestion> questions = new ArrayList<>();
    List<ModPlanOption<?>> searchOptions = TextOccurrencesUtil.isSearchTextOccurrencesEnabled(target)
                                           ? List.of(ModPlanRenameOptions.SEARCH_IN_COMMENTS, ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES)
                                           : List.of(ModPlanRenameOptions.SEARCH_IN_COMMENTS);
    questions.add(ModPlanQuestion.Form.of(searchOptions, searchStorage));
    DumbService dumbService = DumbService.getInstance(target.getProject());
    List<AutomaticRenamerFactory> applicableFactories = ContainerUtil.filter(
      AutomaticRenamerFactory.EP_NAME.getExtensionList(), factory -> dumbService.isUsableInCurrentContext(factory) && factory.isApplicable(target));
    if (applicableFactories.isEmpty()) return List.copyOf(questions);
    questions.add(ModPlanQuestion.Form.of(List.of(ModPlanRenameOptions.RENAME_RELATED), RelatedStorage.INSTANCE));
    Map<ModPlanOption<Boolean>, List<AutomaticRenamerFactory>> factoriesByOption = new LinkedHashMap<>();
    for (AutomaticRenamerFactory factory : applicableFactories) {
      ModPlanOption<Boolean> renamerOption = ModPlanRenameOptions.optionForRenamer(factory);
      if (renamerOption != null) {
        factoriesByOption.computeIfAbsent(renamerOption, _ -> new ArrayList<>()).add(factory);
      }
    }
    factoriesByOption.forEach((renamerOption, factories) -> {
      questions.add(ModPlanQuestion.Form.of(List.of(renamerOption), new RenamerStorage(renamerOption, List.copyOf(factories))));
    });
    return List.copyOf(questions);
  }

  /**
   * @return the edit that renames a named element; a file or a directory is moved to its new name
   */
  @RequiresReadLock
  public static @NotNull ModPlanEdit declarationEdit(@NotNull PsiElement element, @NotNull String newName) {
    if (element instanceof PsiFileSystemItem item) {
      VirtualFile file = item.getVirtualFile();
      VirtualFile parent = file == null ? null : file.getParent();
      if (file != null && parent != null) {
        return new ModPlanEdit.Move(file, parent, newName);
      }
    }
    // A light element, such as the field of a Kotlin enum entry, is renamed through its source, as its setName renames the source.
    PsiElement source = element.getNavigationElement();
    PsiElement written = !element.isPhysical() && source != element && source.isPhysical() && source instanceof PsiNamedElement ? source : element;
    return ModPlanEdit.update(written, copy -> ((PsiNamedElement)copy).setName(newName));
  }

  /**
   * @return the edits that give {@code references} the new name: one {@link ModPlanEdit.UpdateRange} for each range of a reference in
   * its file, so an earlier edit can move the reference or build its element again. The write finds every reference of the range before
   * it renames the first one, as the rename changes the range: the getter and the setter of {@code a.size++}.
   * <p>
   * A {@link FileReference} to a file or a directory is bound to it, which still has the old name, and then its segment gets the new
   * name. The legacy rename binds it after the rename, and a rename keeps the directory, so the two paths differ only in that segment.
   * The bind can change the count of the segments before the target, so the segment is counted from the end of the path.
   * <p>
   * Null if a reference cannot get the new name, see {@link #isRenamedByName}
   */
  @RequiresReadLock
  public static @Nullable List<ModPlanEdit> referenceEdits(@NotNull List<UsageInfo> references, @NotNull String newName) {
    Map<Pair<PsiFile, TextRange>, Map<Integer, SmartPsiElementPointer<PsiFileSystemItem>>> boundTargetsByPlace = new LinkedHashMap<>();
    for (UsageInfo usage : references) {
      PsiReference reference = usage.getReference();
      if (reference == null || !isRenamedByName(reference)) return null;
      PsiFile file = reference.getElement().getContainingFile();
      Pair<PsiFile, TextRange> place = Pair.create(file, rangeInFile(reference));
      SmartPsiElementPointer<PsiFileSystemItem> boundTarget =
        reference instanceof FileReference && reference.resolve() instanceof PsiFileSystemItem target
        ? SmartPointerManager.getInstance(file.getProject()).createSmartPsiElementPointer(target)
        : null;
      boundTargetsByPlace.computeIfAbsent(place, _ -> new LinkedHashMap<>()).put(indexAt(reference), boundTarget);
    }
    List<ModPlanEdit> result = new ArrayList<>();
    for (Map.Entry<Pair<PsiFile, TextRange>, Map<Integer, SmartPsiElementPointer<PsiFileSystemItem>>> entry : boundTargetsByPlace.entrySet()) {
      PsiFile file = entry.getKey().getFirst();
      SmartPsiFileRange place =
        SmartPointerManager.getInstance(file.getProject()).createSmartPsiFileRangePointer(file, entry.getKey().getSecond());
      Map<Integer, SmartPsiElementPointer<PsiFileSystemItem>> boundTargets = new LinkedHashMap<>(entry.getValue());
      result.add(new ModPlanEdit.UpdateRange(place, (copy, range) -> {
        List<PsiReference> referencesOfRange = referencesAt(copy, range);
        Map<PsiReference, SmartPsiElementPointer<PsiFileSystemItem>> renamedReferences = new LinkedHashMap<>();
        for (Map.Entry<Integer, SmartPsiElementPointer<PsiFileSystemItem>> indexEntry : boundTargets.entrySet()) {
          int index = indexEntry.getKey();
          if (index >= referencesOfRange.size()) {
            throw new IncorrectOperationException("No reference at " + range + " in " + copy);
          }
          renamedReferences.put(referencesOfRange.get(index), indexEntry.getValue());
        }
        renamedReferences.forEach((reference, boundTarget) -> {
          PsiFileSystemItem target = boundTarget == null ? null : boundTarget.getElement();
          if (target != null && reference instanceof FileReference fileReference) {
            bindAndRename(fileReference, target, newName);
          }
          else {
            reference.handleElementRename(newName);
          }
        });
      }));
    }
    return result;
  }

  private static void bindAndRename(@NotNull FileReference reference, @NotNull PsiFileSystemItem target, @NotNull String newName) {
    FileReferenceSet referenceSet = reference.getFileReferenceSet();
    int segmentsAfter = referenceSet.getAllReferences().length - 1 - reference.getIndex();
    int setStart = referenceSet.getStartInElement();
    PsiElement bound;
    try {
      bound = reference.bindToElement(target);
    }
    catch (IncorrectOperationException e) {
      // As RenameUtilBase.renameReference: a reference that cannot be bound gets the new name.
      reference.handleElementRename(newName);
      return;
    }
    for (PsiReference boundReference : bound.getReferences()) {
      if (boundReference.getClass() == reference.getClass()) {
        FileReference boundFileReference = (FileReference)boundReference;
        if (boundFileReference.getFileReferenceSet().getStartInElement() == setStart) {
          FileReference[] boundSegments = boundFileReference.getFileReferenceSet().getAllReferences();
          int targetIndex = boundSegments.length - 1 - segmentsAfter;
          if (targetIndex < 0) break;
          boundSegments[targetIndex].handleElementRename(newName);
          return;
        }
      }
    }
    throw new IncorrectOperationException("No segment of " + target + " in " + bound);
  }

  /**
   * @return whether {@link #referenceEdits} writes {@code reference} as the legacy rename does. The legacy rename binds a
   * {@link BindablePsiReference} to the renamed element after the write, see {@code RenameUtilBase.renameReference}. A fragment-only
   * reference gets the new name there too. A {@link FileReference} to a file or a directory is bound, then renamed. False for another
   * bindable reference, and if the write cannot find the reference again in the copy, see {@link #indexAt}.
   */
  @RequiresReadLock
  public static boolean isRenamedByName(@NotNull PsiReference reference) {
    if (indexAt(reference) < 0) return false;
    if (!(reference instanceof BindablePsiReference)) return true;
    if (reference instanceof FragmentaryPsiReference fragmentary && fragmentary.isFragmentOnlyRename()) return true;
    return reference instanceof FileReference && reference.resolve() != null;
  }

  /**
   * @return the references at exactly {@code range} of {@code file}, as {@code MoveRenameUsageInfo.getReference} finds a reference again:
   * the ones of the elements at the range, from the leaf to the root and in the order of their providers, or else the ones that
   * {@code findReferenceAt} gives at the range. The copy of a file has the same text, so it gives the same references in the same order.
   */
  private static @NotNull List<PsiReference> referencesAt(@NotNull PsiFile file, @NotNull TextRange range) {
    List<PsiReference> references = new ArrayList<>();
    for (PsiElement element = file.findElementAt(range.getStartOffset());
         element != null && !(element instanceof PsiFile);
         element = element.getParent()) {
      int elementStart = element.getTextRange().getStartOffset();
      references.addAll(ContainerUtil.filter(element.getReferences(),
                                             reference -> reference.getRangeInElement().shiftRight(elementStart).equals(range)));
    }
    if (!references.isEmpty()) return references;
    PsiReference found = file.findReferenceAt(range.getStartOffset());
    PsiReference[] foundReferences = found instanceof PsiMultiReference multiReference ? multiReference.getReferences()
                                                                                        : found == null ? PsiReference.EMPTY_ARRAY
                                                                                                        : new PsiReference[]{found};
    return ContainerUtil.filter(foundReferences, reference -> rangeInFile(reference).equals(range));
  }

  private static @NotNull TextRange rangeInFile(@NotNull PsiReference reference) {
    return reference.getRangeInElement().shiftRight(reference.getElement().getTextRange().getStartOffset());
  }

  /**
   * A search can make its own reference object. Then the index is the one of the first reference of the same class at the range.
   *
   * @return the index of {@code reference} among the references at its range, see {@link #referencesAt}; -1 if the range has no
   * reference, or if several references have the range and none of them is {@code reference} or has its class
   */
  private static int indexAt(@NotNull PsiReference reference) {
    PsiFile file = reference.getElement().getContainingFile();
    if (file == null) return -1;
    List<PsiReference> references = referencesAt(file, rangeInFile(reference));
    if (references.size() <= 1) return references.size() - 1;
    int index = ContainerUtil.indexOfIdentity(references, reference);
    return index >= 0 ? index : ContainerUtil.indexOf(references, other -> other.getClass() == reference.getClass());
  }

  /**
   * @param usages what the search of {@code element} found, as {@code RenameUtil.findUsages} finds them
   * @return the usages of {@code usages} that belong to {@code element}: as in the legacy rename, a usage belongs to the element it refers
   * to, so the calls of an overrider that the search of a method also finds are the overrider's; and the collisions that the search found
   * that refer to no renamed element, such as the declaration that the new name redeclares, which the legacy rename takes as conflicts too
   */
  @RequiresReadLock
  public static @NotNull @Unmodifiable List<UsageInfo> ownUsages(@NotNull PsiElement element,
                                                                 @NotNull Map<PsiElement, String> renamed,
                                                                 @NotNull List<UsageInfo> usages) {
    MultiMap<PsiElement, UsageInfo> classified = RenameProcessor.classifyUsages(renamed.keySet(), usages);
    List<UsageInfo> result = new ArrayList<>(classified.get(element));
    Set<UsageInfo> classifiedUsages = new HashSet<>(classified.values());
    for (UsageInfo usage : usages) {
      if (usage instanceof UnresolvableCollisionUsageInfo && !classifiedUsages.contains(usage)) {
        result.add(usage);
      }
    }
    return List.copyOf(result);
  }

  /** Keeps the general switch of the related renames in the setting with which the legacy rename leaves them. */
  private enum RelatedStorage implements ModPlanQuestion.Storage {
    INSTANCE;

    @Override
    public @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues options) {
      return options.with(ModPlanRenameOptions.RENAME_RELATED, RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG);
    }

    @Override
    public void store(@NotNull ModPlanOptionValues answers) {
      RefactoringSettings.getInstance().RENAME_SHOW_AUTOMATIC_RENAMING_DIALOG = answers.get(ModPlanRenameOptions.RENAME_RELATED);
    }
  }

  /** Keeps a switch in the settings of its factories, as the checkbox of the rename dialog does. */
  private record RenamerStorage(@NotNull ModPlanOption<Boolean> renamerOption, @NotNull List<AutomaticRenamerFactory> factories)
    implements ModPlanQuestion.Storage {
    @Override
    public @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues options) {
      return options.with(renamerOption, factories.getFirst().isEnabled());
    }

    @Override
    public void store(@NotNull ModPlanOptionValues answers) {
      for (AutomaticRenamerFactory factory : factories) {
        factory.setEnabled(answers.get(renamerOption));
      }
    }
  }
}
