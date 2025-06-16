// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ReflectionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * While it implements Cloneable, this is just a technical thing to implement
 * {@link #getFileModifierForPreview(PsiFile)} default implementation.
 * Calling {@link #clone()} is useless and will result in {@link CloneNotSupportedException}. 
 */
public abstract class LocalQuickFixOnPsiElement implements LocalQuickFix, Cloneable {
  protected static final Logger LOG = Logger.getInstance(LocalQuickFixOnPsiElement.class);
  @SafeFieldForPreview // not actually safe but will be properly patched in getFileModifierForPreview 
  protected final SmartPsiElementPointer<PsiElement> myStartElement;
  @SafeFieldForPreview // not actually safe but will be properly patched in getFileModifierForPreview 
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

  @Override
  public final @NotNull String getName() {
    return getText();
  }

  // validity of startElement/endElement must be checked before calling this
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile psiFile,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    return true;
  }

  protected boolean isAvailable() {
    if (myStartElement == null) return false;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    PsiFile psiFile = myStartElement.getContainingFile();
    Project project = myStartElement.getProject();
    return startElement != null &&
           endElement != null &&
           psiFile != null &&
           isAvailable(project, psiFile, startElement, endElement);
  }

  public PsiElement getStartElement() {
    return myStartElement == null ? null : myStartElement.getElement();
  }

  public PsiElement getEndElement() {
    return myEndElement == null ? null : myEndElement.getElement();
  }

  public abstract @IntentionName @NotNull String getText();

  @Override
  public final void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    applyFix();
  }

  public void applyFix() {
    if (myStartElement == null) return;
    final PsiElement startElement = myStartElement.getElement();
    final PsiElement endElement = myEndElement == null ? startElement : myEndElement.getElement();
    if (startElement == null || endElement == null) return;
    PsiFile psiFile = startElement.getContainingFile();
    if (psiFile == null) return;
    invoke(psiFile.getProject(), psiFile, startElement, endElement);
  }

  public abstract void invoke(@NotNull Project project,
                              @NotNull PsiFile psiFile,
                              @NotNull PsiElement startElement,
                              @NotNull PsiElement endElement);

  /**
   * {@inheritDoc}
   * <p>
   * This implementation clones current intention replacing {@link #myStartElement} and
   * {@link #myEndElement} field values with the pointers to the corresponding elements
   * in the target file. It returns null if subclass has potentially unsafe fields not
   * marked with {@link SafeFieldForPreview @SafeFieldForPreview}.
   */
  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    // Check field safety in subclass
    if (LocalQuickFix.super.getFileModifierForPreview(target) != this) return null;
    PsiElement startElement = getStartElement();
    PsiElement endElement = getEndElement();
    if (startElement == null && myStartElement != null || 
        endElement == null && myEndElement != null) return null;
    if (startElement != null && startElement.getContainingFile() != target.getOriginalFile()) return null;
    startElement = PsiTreeUtil.findSameElementInCopy(startElement, target);
    endElement = PsiTreeUtil.findSameElementInCopy(endElement, target);
    LocalQuickFixOnPsiElement clone;
    try {
      clone = (LocalQuickFixOnPsiElement)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new InternalError(e); // should not happen as we implement Cloneable
    }
    if (startElement != null) {
      ReflectionUtil.setField(LocalQuickFixOnPsiElement.class, clone, SmartPsiElementPointer.class, "myStartElement",
                              SmartPointerManager.createPointer(startElement));
    }
    if (endElement != null) {
      ReflectionUtil.setField(LocalQuickFixOnPsiElement.class, clone, SmartPsiElementPointer.class, "myEndElement",
                              SmartPointerManager.createPointer(endElement));
    }
    return clone;
  }

  @Override
  public @Nullable PsiElement getElementToMakeWritable(@NotNull PsiFile currentFile) {
    if (!startInWriteAction()) return null;
    if (myStartElement != null) return myStartElement.getContainingFile();
    return currentFile;
  }

  /**
   * @throws CloneNotSupportedException always
   * @deprecated do not call this method, it's non-functional
   */
  @Deprecated
  @Override
  protected Object clone() throws CloneNotSupportedException {
    throw new CloneNotSupportedException();
  }
}
