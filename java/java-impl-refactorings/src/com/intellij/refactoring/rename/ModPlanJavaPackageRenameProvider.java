// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.rename;

import com.intellij.java.JavaBundle;
import com.intellij.model.SymbolPointer;
import com.intellij.openapi.util.NlsContexts.DialogMessage;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiPackage;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.modplan.ModPlanAnalysisResult;
import com.intellij.refactoring.modplan.ModPlanConflicts;
import com.intellij.refactoring.modplan.ModPlanEdit;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanQuestion;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions;
import com.intellij.refactoring.modplan.rename.ModPlanRenameProviderBase;
import com.intellij.refactoring.modplan.rename.ModPlanRenameUtil;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The rename of a package on the ModPlan engine, as {@link RenamePsiPackageProcessor} renames it. The directories of the package are renamed
 * with it, each by the provider of a directory. The references to the package get the new name. A package that is the prefix of a source
 * root is not supported, as its rename changes the module model.
 */
@ApiStatus.Internal
public final class ModPlanJavaPackageRenameProvider extends ModPlanRenameProviderBase {
  /** The legacy rename gives {@code subject}, a package, to {@link RenamePsiPackageProcessor}. */
  @Override
  public boolean isOwner(@NotNull SymbolPointer subject) {
    return subject.element() instanceof PsiPackage psiPackage &&
           RenamePsiElementProcessor.forElement(psiPackage) instanceof RenamePsiPackageProcessor;
  }

  @Override
  public boolean isLegacyOnly(@NotNull SymbolPointer subject) {
    return subject.element() instanceof PsiPackage psiPackage && RenamePsiPackageProcessor.changesPackagePrefix(psiPackage);
  }

  @Override
  public ModPlanQuestion.@NotNull Storage searchStorage(@NotNull SymbolPointer target) {
    return new PackageSearchStorage();
  }

  /**
   * The directories of the package, as {@link RenamePsiPackageProcessor#preparePackageRenaming} renames them; a name that moves the
   * package renames no directory, see {@link #analyze}.
   */
  @Override
  protected void prepareRenaming(@NotNull PsiElement element,
                                 @NotNull String newName,
                                 @NotNull Map<PsiElement, String> renamed,
                                 @NotNull ModPlanOptionValues answers) {
    if (element instanceof PsiPackage psiPackage && !parentPackageIsChanged(psiPackage, newName)) {
      RenamePsiPackageProcessor.preparePackageRenaming(psiPackage, newName, renamed);
    }
  }

  /**
   * The references get the new name; the package itself has no declaration to write, as its directories are renamed by their own edits. A
   * qualified name with the parent of the package renames it to the short name, as the rename dialog does. A qualified name with another
   * parent asks for a move, which the rename dialog does with a move, not with a rename: it is a conflict, and nothing is edited.
   */
  @Override
  protected @NotNull ModPlanAnalysisResult analyze(@NotNull PsiElement element,
                                                   @NotNull String requestedName,
                                                   @NotNull List<UsageInfo> usages,
                                                   @NotNull Map<PsiElement, String> renamed) {
    if (!(element instanceof PsiPackage psiPackage)) {
      return new ModPlanAnalysisResult.Refused(ModPlanAnalysisResult.RefusedReason.NOT_APPLICABLE);
    }
    if (parentPackageIsChanged(psiPackage, requestedName)) {
      ModPlanConflicts conflicts = ModPlanConflicts.builder()
        .broken(psiPackage, JavaBundle.message("rename.package.move.not.supported.conflict", requestedName)).build();
      return new ModPlanAnalysisResult.Found(List.of(), conflicts);
    }
    return super.analyze(element, StringUtil.getShortName(requestedName), usages, renamed);
  }

  @Override
  protected @NotNull List<ModPlanEdit> declarationEdits(@NotNull PsiElement element, @NotNull String newName) {
    return List.of();
  }

  /** As the legacy rename, a name that moves the package has no conflicts of the new name, see {@link #analyze}. */
  @Override
  protected @NotNull MultiMap<PsiElement, @DialogMessage String> nameConflicts(@NotNull PsiElement element,
                                                                              @NotNull String requestedName,
                                                                              @NotNull Map<PsiElement, String> renamed) {
    MultiMap<PsiElement, @DialogMessage String> conflicts = new MultiMap<>();
    if (element instanceof PsiPackage psiPackage && !parentPackageIsChanged(psiPackage, requestedName)) {
      RenamePsiPackageProcessor.findExistingPackageNameConflicts(psiPackage, StringUtil.getShortName(requestedName), conflicts);
    }
    return conflicts;
  }

  /**
   * @return whether {@code newName} is a qualified name with another parent than the one of {@code psiPackage}, as the rename dialog decides
   *
   * <pre>{@code
   * // the package a.b.c
   * changesParentPackage(a.b.c, "x.y")     // true: the parent changes from a.b to x
   * changesParentPackage(a.b.c, "a.b.d")   // false: the parent stays a.b
   * changesParentPackage(a.b.c, "d")       // false: a name with no parent is a short name
   * }</pre>
   */
  private static boolean parentPackageIsChanged(@NotNull PsiPackage psiPackage, @NotNull String newName) {
    String parentName = StringUtil.getPackageName(newName);
    return !parentName.isEmpty() && !parentName.equals(StringUtil.getPackageName(psiPackage.getQualifiedName()));
  }

  /** @return the usages of {@code element}, as {@code RenameUtil.findUsages} finds them with {@link RenamePsiPackageProcessor} */
  @Override
  protected @NotNull List<UsageInfo> usagesOf(@NotNull PsiElement element,
                                              @NotNull String requestedName,
                                              @NotNull ModPlanRequest request,
                                              @NotNull Map<PsiElement, String> renamed) {
    if (element instanceof PsiPackage psiPackage && parentPackageIsChanged(psiPackage, requestedName)) return List.of();
    String newName = StringUtil.getShortName(requestedName);
    boolean inComments = request.optionValues().get(ModPlanRenameOptions.SEARCH_IN_COMMENTS);
    boolean textOccurrences = request.optionValues().get(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES);
    SearchScope searchScope = request.searchScope().get();
    List<UsageInfo> usages = new ArrayList<>();
    if (element instanceof PsiPackage psiPackage) {
      RenameUtil.addReferenceUsages(element, ReferencesSearch.search(element, RenameUtil.useScopeOf(element, searchScope)).findAll(),
                                    textOccurrences,
                                    reference -> RenameUtilBase.createMoveRenameUsageInfo(element, reference, reference.getElement()), usages);
      RenameUtil.addNonCodeUsages(element, element, newName, searchScope, inComments, textOccurrences,
                                  RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, false),
                                  RenamePsiPackageProcessor.getPackageQualifiedNameAfterRename(psiPackage, newName, true), null, usages);
    }
    return ModPlanRenameUtil.ownUsages(element, renamed, usages);
  }

  /** Keeps the search options in the settings of a package, as the rename dialog does. */
  private record PackageSearchStorage() implements ModPlanQuestion.Storage {
    @Override
    public @NotNull ModPlanOptionValues load(@NotNull ModPlanOptionValues options) {
      JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      return options.with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, settings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE)
        .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, settings.RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE);
    }

    @Override
    public void store(@NotNull ModPlanOptionValues answers) {
      JavaRefactoringSettings settings = JavaRefactoringSettings.getInstance();
      settings.RENAME_SEARCH_IN_COMMENTS_FOR_PACKAGE = answers.get(ModPlanRenameOptions.SEARCH_IN_COMMENTS);
      settings.RENAME_SEARCH_FOR_TEXT_FOR_PACKAGE = answers.get(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES);
    }
  }
}
