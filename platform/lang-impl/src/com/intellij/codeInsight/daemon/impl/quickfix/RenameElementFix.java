// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.rename.RenameProcessor;
import com.intellij.refactoring.rename.RenameUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RenameElementFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(RenameElementFix.class);

  private final String myNewName;
  private final @IntentionName String myText;

  public RenameElementFix(@NotNull PsiNamedElement element) {
    super(element);
    VirtualFile vFile = element.getContainingFile().getVirtualFile();
    assert vFile != null : element;
    myNewName = vFile.getNameWithoutExtension();
    myText = CodeInsightBundle.message("rename.public.class.text", element.getName(), myNewName);
  }

  public RenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName) {
    this(element, newName, CodeInsightBundle.message("rename.named.element.text", element.getName(), newName));
  }

  public RenameElementFix(@NotNull PsiNamedElement element, @NotNull String newName, @NotNull @IntentionName String text) {
    super(element);
    myNewName = newName;
    myText = text;
  }

  @Override
  public @NotNull String getText() {
    return myText;
  }

  @Override
  public @NotNull String getFamilyName() {
    return CodeInsightBundle.message("rename.element.family");
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile psiFile,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    if (isAvailable(project, null, psiFile)) {
      LOG.assertTrue(psiFile == startElement.getContainingFile());
      if (!FileModificationService.getInstance().prepareFileForWrite(psiFile)) return;
      RenameProcessor processor = new RenameProcessor(project, startElement, myNewName, false, false);
      processor.run();
    }
  }

  @Override
  public @NotNull IntentionPreviewInfo generatePreview(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    PsiElement element = PsiTreeUtil.findSameElementInCopy(getStartElement(), psiFile);
    if (!(element instanceof PsiNamedElement named)) return IntentionPreviewInfo.EMPTY;
    named.setName(myNewName);
    return IntentionPreviewInfo.DIFF;
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return RenameUtil.isValidName(project, startElement, myNewName);
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}