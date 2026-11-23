// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.modplan.ModPlanCommandAction;
import com.intellij.refactoring.modplan.ModPlanOptionValues;
import com.intellij.refactoring.modplan.ModPlanRequest;
import com.intellij.refactoring.modplan.rename.ModPlanRenameOptions;
import com.intellij.refactoring.modplan.rename.ModPlanRenameRefactoring;
import com.intellij.refactoring.rename.RenameUtil;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The rename of an element to a given name on the ModPlan engine, as a {@link com.intellij.modcommand.ModCommandAction}. Create it with
 * {@link com.intellij.codeInsight.intention.QuickFixFactory#createRenameElementFix(PsiNamedElement)} or
 * {@link com.intellij.codeInsight.intention.QuickFixFactory#createModPlanRenameFix}. As the legacy {@link RenameElementFix}, it renames
 * the element itself, searches no comments and no text occurrences, and runs the related renames that the settings turn on. It is not
 * offered for an element that the ModPlan rename does not support. See {@link ModPlanCommandAction} for the differences from the legacy
 * flow.
 */
@ApiStatus.Internal
public final class ModPlanRenameFix extends ModPlanCommandAction {
  private final @NotNull String myNewName;

  /** The rename of {@code element} to the name of its file. */
  @RequiresReadLock
  public ModPlanRenameFix(@NotNull PsiNamedElement element) {
    this(element, fileNameOf(element), CodeInsightBundle.message("rename.public.class.text", element.getName(), fileNameOf(element)));
  }

  @RequiresReadLock
  public ModPlanRenameFix(@NotNull PsiNamedElement element, @NotNull String newName) {
    this(element, newName, CodeInsightBundle.message("rename.named.element.text", element.getName(), newName));
  }

  @RequiresReadLock
  private ModPlanRenameFix(@NotNull PsiNamedElement element, @NotNull String newName, @NotNull @IntentionName String text) {
    ModPlanOptionValues optionValues = ModPlanRenameOptions.withRenamersOfSettings(ModPlanOptionValues.EMPTY)
      .with(ModPlanRenameOptions.SEARCH_IN_COMMENTS, false)
      .with(ModPlanRenameOptions.SEARCH_TEXT_OCCURRENCES, false);
    ModPlanRequest request = ModPlanRenameRefactoring.request(element.getProject(), element, newName, false, optionValues);
    super(ModPlanRenameRefactoring.getInstance(), request, element, text);
    myNewName = newName;
  }

  private static @NotNull String fileNameOf(@NotNull PsiNamedElement element) {
    VirtualFile file = element.getContainingFile().getVirtualFile();
    assert file != null : element;
    return file.getNameWithoutExtension();
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("rename.element.family");
  }

  @Override
  public @Nullable Presentation getPresentation(@NotNull ActionContext context) {
    PsiElement element = getAnchor();
    if (element == null || element.getContainingFile() != context.file()) return null;
    if (!RenameUtil.isValidName(context.project(), element, myNewName)) return null;
    return super.getPresentation(context);
  }

  /** The new name of the element alone, as the usages are not searched for the preview. */
  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull ActionContext context) {
    PsiElement element = getAnchor();
    if (!(element instanceof PsiNamedElement)) return IntentionPreviewInfo.EMPTY;
    ModCommand rename = ModCommand.psiUpdate(element, (copy, _) -> ((PsiNamedElement)copy).setName(myNewName));
    return IntentionPreviewUtils.getModCommandPreview(rename, context);
  }
}
