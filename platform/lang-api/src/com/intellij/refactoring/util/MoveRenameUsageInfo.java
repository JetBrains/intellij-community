/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.refactoring.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.RangeMarker;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;
import org.jetbrains.annotations.Nullable;

public class MoveRenameUsageInfo extends UsageInfo{
  private static final Logger LOG = Logger.getInstance("#" + MoveRenameUsageInfo.class.getName());
  private SmartPsiElementPointer myReferencedElementPointer = null;
  private PsiElement myReferencedElement;

  private PsiReference myReference;
  private RangeMarker myReferenceRangeMarker = null;

  public MoveRenameUsageInfo(PsiReference reference, PsiElement referencedElement){
    this(reference.getElement(), reference, referencedElement);
  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, PsiElement referencedElement){
    super(element);
    init(element, reference, referencedElement);

  }

  public MoveRenameUsageInfo(PsiElement element, PsiReference reference, int startOffset, int endOffset, PsiElement referencedElement, boolean nonCodeUsage){
    super(element, startOffset, endOffset, nonCodeUsage);
    init(element, reference, referencedElement);
  }

  private void init(final PsiElement element, PsiReference reference, final PsiElement referencedElement) {
    final Project project = element.getProject();
    myReferencedElement = referencedElement;
    if (referencedElement != null) {
      myReferencedElementPointer = SmartPointerManager.getInstance(project).createSmartPsiElementPointer(referencedElement);
    }
    if (reference == null) reference = element.getReference();
    PsiFile containingFile = element.getContainingFile();
    if (reference == null) {
      final TextRange textRange = element.getTextRange();
      if (textRange != null) {
        reference = containingFile.findReferenceAt(textRange.getStartOffset());
      }
    }
    myReference = reference;
    if (reference != null) {
      Document document = PsiDocumentManager.getInstance(project).getDocument(containingFile);
      if (document != null) {
        final int elementStart = reference.getElement().getTextRange().getStartOffset();
        final TextRange rangeInElement = reference.getRangeInElement();
        LOG.assertTrue(elementStart + rangeInElement.getEndOffset() <= document.getTextLength(), reference);
        myReferenceRangeMarker = document.createRangeMarker(elementStart + rangeInElement.getStartOffset(),
                                                            elementStart + rangeInElement.getEndOffset());
      }
      myDynamicUsage = reference.resolve() == null;
    }
  }

  @Nullable
  public PsiElement getUpToDateReferencedElement() {
    return myReferencedElementPointer == null ? null : myReferencedElementPointer.getElement();
  }

  @Nullable
  public PsiElement getReferencedElement() {
    return myReferencedElement;
  }

  @Override
  @Nullable
  public PsiReference getReference() {
    if (myReference != null) {
      final PsiElement element = myReference.getElement();
      if (element != null && element.isValid()) {
        if (myReferenceRangeMarker == null) {
          return myReference;
        }

        final PsiReference reference = checkReferenceRange(element, new Function<Integer, PsiReference>() {
          @Override
          public PsiReference fun(Integer start) {
            return myReference;
          }
        });

        if (reference != null) {
          return reference;
        }
      }
    }

    if (myReferenceRangeMarker == null) return null;
    final PsiElement element = getElement();
    if (element == null || !element.isValid()) {
      return null;
    }
    return checkReferenceRange(element, new Function<Integer, PsiReference>() {
      @Override
      public PsiReference fun(Integer start) {
        return element.findReferenceAt(start);
      }
    });
  }

  @Nullable
  private PsiReference checkReferenceRange(PsiElement element, Function<Integer, PsiReference> fn) {
    final int start = myReferenceRangeMarker.getStartOffset() - element.getTextRange().getStartOffset();
    final int end = myReferenceRangeMarker.getEndOffset() - element.getTextRange().getStartOffset();
    final PsiReference reference = fn.fun(start);
    if (reference == null) {
      return null;
    }
    final TextRange rangeInElement = reference.getRangeInElement();
    if (rangeInElement.getStartOffset() != start || rangeInElement.getEndOffset() != end) {
      return null;
    }
    return reference;
  }
}
