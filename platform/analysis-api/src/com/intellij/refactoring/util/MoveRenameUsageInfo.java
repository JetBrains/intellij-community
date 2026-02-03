// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveRenameUsageInfo extends UsageInfo implements Cloneable {
  private static final Logger LOG = Logger.getInstance(MoveRenameUsageInfo.class);
  private SmartPsiElementPointer<?> myReferencedElementPointer;
  private PsiElement myReferencedElement;

  private PsiReference myReference;
  private RangeMarker myReferenceRangeMarker;

  public MoveRenameUsageInfo(@NotNull PsiReference reference, PsiElement referencedElement){
    this(reference.getElement(), reference, referencedElement);
  }

  public MoveRenameUsageInfo(@NotNull PsiElement element, PsiReference reference, PsiElement referencedElement){
    super(element);
    init(element, reference, referencedElement);
  }

  public MoveRenameUsageInfo(@NotNull PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    init(element, reference, referencedElement);
  }

  private void init(@NotNull PsiElement element, @Nullable PsiReference reference, @Nullable PsiElement referencedElement) {
    Project project = element.getProject();
    myReferencedElement = referencedElement;
    if (referencedElement != null) {
      myReferencedElementPointer = SmartPointerManager.getInstance(referencedElement.getProject()).createSmartPsiElementPointer(referencedElement);
    }
    if (reference == null) {
      reference = element.getReference();
    }
    PsiFile containingFile = element.getContainingFile();
    if (reference == null) {
      TextRange textRange = element.getTextRange();
      if (textRange != null) {
        reference = containingFile.findReferenceAt(textRange.getStartOffset());
      }
    }
    myReference = reference;
    if (reference != null) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
      if (document != null) {
        int elementStart = reference.getElement().getTextRange().getStartOffset();
        TextRange rangeInElement = reference.getRangeInElement();
        LOG.assertTrue(elementStart + rangeInElement.getEndOffset() <= document.getTextLength(), reference);
        myReferenceRangeMarker = document.createRangeMarker(elementStart + rangeInElement.getStartOffset(),
                                                            elementStart + rangeInElement.getEndOffset());
      }
      myDynamicUsage = reference.resolve() == null;
    }
  }

  public @Nullable PsiElement getUpToDateReferencedElement() {
    return myReferencedElementPointer == null ? null : myReferencedElementPointer.getElement();
  }

  public @Nullable PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  @Override
  public @Nullable PsiReference getReference() {
    if (myReference != null) {
      PsiElement element = myReference.getElement();
      if (element.isValid()) {
        if (myReferenceRangeMarker == null) {
          return myReference;
        }

        PsiReference reference = checkReferenceRange(element, start -> myReference);

        if (reference != null) {
          return reference;
        }
      }
    }

    if (myReferenceRangeMarker == null) return null;
    PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return null;
    }
    return checkReferenceRange(element, start -> element.findReferenceAt(start));
  }

  /**
   * Range that is used to check if the reference was not changed.
   * Can be overridden in case of reference was modified deliberately.
   *
   * @param element of the reference.
   * @return range of the reference in the document.
   */
  protected @NotNull Segment getReferenceRangeToCheck(@NotNull PsiElement element) {
    return myReferenceRangeMarker;
  }

  private @Nullable PsiReference checkReferenceRange(@NotNull PsiElement element, @NotNull Function<? super Integer, ? extends PsiReference> fn) {
    var rangeToCheck = getReferenceRangeToCheck(element);
    int start = rangeToCheck.getStartOffset() - element.getTextRange().getStartOffset();
    int end = rangeToCheck.getEndOffset() - element.getTextRange().getStartOffset();
    PsiReference reference = fn.fun(start);
    if (reference == null) {
      return null;
    }
    TextRange rangeInElement = reference.getRangeInElement();
    if (rangeInElement.getStartOffset() != start || rangeInElement.getEndOffset() != end) {
      return null;
    }
    return reference;
  }

  private static boolean isPackage(PsiElement element) {
    return element instanceof PsiDirectoryContainer && element.getContainingFile() == null;
  }
}
