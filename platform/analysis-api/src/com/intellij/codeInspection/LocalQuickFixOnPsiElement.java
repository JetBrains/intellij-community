// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public abstract class LocalQuickFixOnPsiElement implements LocalQuickFix {
  protected static final Logger LOG = Logger.getInstance(LocalQuickFixOnPsiElement.class);
  protected final SmartPsiElementPointer<PsiElement> myStartElement;
  protected final SmartPsiElementPointer<PsiElement> myEndElement;

  protected LocalQuickFixOnPsiElement(@NotNull PsiElement element) {
    this(element, element);
  }

  public LocalQuickFixOnPsiElement(PsiElement startElement, PsiElement endElement) {
    if (startElement == null || endElement == null) {
      myStartElement = myEndElement = null;
      return;
    }
    LOG.assertTrue(startElement.isValid());
    PsiFile startContainingFile = startElement.getContainingFile();
    PsiFile endContainingFile = startElement == endElement ? startContainingFile : endElement.getContainingFile();
    if (startElement != endElement) {
      LOG.assertTrue(endElement.isValid());
      LOG.assertTrue(startContainingFile == endContainingFile, "Both elements must be from the same file");
    }
    Project project = startContainingFile == null ? startElement.getProject() : startContainingFile.getProject(); // containingFile can be null for a directory
    myStartElement = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(startElement, startContainingFile);
    myEndElement = endElement == startElement ? null : SmartPointerManager.getInstance(project).createSmartPsiElementPointer(endElement, endContainingFile);
  }

  @NotNull
  @Override
  public final String getName() {
    return getText();
  }

  // validity of startElement/endElement must be checked before calling this
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return true;
  }

  protected boolean isAvailable() {
    if (myStartElement == null) return false;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    PsiFile file = myStartElement.getContainingFile();
    Project project = myStartElement.getProject();
    return startElement != null &&
           endElement != null &&
           file != null &&
           isAvailable(project, file, startElement, endElement);
  }

  public PsiElement getStartElement() {
    return myStartElement == null ? null : myStartElement.getElement();
  }

  public PsiElement getEndElement() {
    return myEndElement == null ? null : myEndElement.getElement();
  }

  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public abstract String getText();

  @Override
  public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix();
  }

  public void applyFix() {
    if (myStartElement == null) return;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    if (startElement == null || endElement == null) return;
    PsiFile file = startElement.getContainingFile();
    if (file == null) return;
    invoke(file.getProject(), file, startElement, endElement);
  }

  public abstract void invoke(@NotNull Project project,
                              @NotNull PsiFile file,
                              @NotNull PsiElement startElement,
                              @NotNull PsiElement endElement);

}
