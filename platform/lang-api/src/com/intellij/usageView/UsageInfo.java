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
package com.intellij.usageView;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ProperTextRange;
import com.intellij.openapi.util.Segment;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class UsageInfo {
  public static final UsageInfo[] EMPTY_ARRAY = new UsageInfo[0];
  private static final Logger LOG = Logger.getInstance("#com.intellij.usageView.UsageInfo");
  private final SmartPsiElementPointer<?> mySmartPointer;
  private final SmartPsiFileRange myPsiFileRange;

  public final boolean isNonCodeUsage;
  protected boolean myDynamicUsage = false;

  public UsageInfo(@NotNull PsiElement element, int startOffset, int endOffset, boolean isNonCodeUsage) {
    LOG.assertTrue(element.isValid(), element);
    element = element.getNavigationElement();
    Project project = element.getProject();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);

    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      LOG.error("text range null for " + element + "; " + element.getClass());
    }
    if (startOffset == -1 && endOffset == -1) {
      // calculate natural element range
      startOffset = element.getTextOffset() - elementRange.getStartOffset();
      endOffset = elementRange.getEndOffset() - elementRange.getStartOffset();
    }

    LOG.assertTrue(startOffset >= 0, startOffset);
    LOG.assertTrue(endOffset >= startOffset, endOffset-startOffset);

    if (startOffset != element.getTextOffset() - elementRange.getStartOffset() || endOffset != elementRange.getLength()) {
      PsiFile file = element.getContainingFile();
      LOG.assertTrue(file != null, element);
      mySmartPointer = smartPointerManager.createSmartPsiElementPointer(element, file);
      myPsiFileRange = smartPointerManager.createSmartPsiFileRangePointer(file, TextRange.create(startOffset, endOffset).shiftRight(elementRange.getStartOffset()));
    }
    else {
      mySmartPointer = smartPointerManager.createSmartPsiElementPointer(element);
      myPsiFileRange = null;
    }
    this.isNonCodeUsage = isNonCodeUsage;
  }

  public UsageInfo(@NotNull PsiElement element, boolean isNonCodeUsage) {
    this(element, -1, -1, isNonCodeUsage);
  }

  public UsageInfo(@NotNull PsiElement element, int startOffset, int endOffset) {
    this(element, startOffset, endOffset, false);
  }

  public UsageInfo(@NotNull PsiReference reference) {
    this(reference.getElement(), reference.getRangeInElement().getStartOffset(), reference.getRangeInElement().getEndOffset());
    myDynamicUsage = reference.resolve() == null;
  }

  public UsageInfo(@NotNull PsiQualifiedReference reference) {
    this((PsiElement)reference);
  }

  public UsageInfo(@NotNull PsiElement element) {
    this(element, false);
  }

  @Nullable
  public PsiElement getElement() { // SmartPointer is used to fix SCR #4572, hotya eto krivo i nado vse perepisat'
    return mySmartPointer.getElement();
  }

  @Nullable
  public PsiReference getReference() {
    PsiElement element = getElement();
    return element == null ? null : element.getReference();
  }

  /**
   * @deprecated for the range in element use {@link #getRangeInElement} instead,
   *             for the whole text range in the file covered by this usage info, use {@link #getSegment()}
   */
  public TextRange getRange() {
    return getRangeInElement();
  }

  /**
   * @return range in element
   */
  @Nullable("null means range is invalid")
  public ProperTextRange getRangeInElement() {
    PsiElement element = getElement();
    if (element == null) return null;
    TextRange elementRange = element.getTextRange();
    ProperTextRange result;
    if (myPsiFileRange == null) {
      int startOffset = element.getTextOffset();
      result = ProperTextRange.create(startOffset, elementRange.getEndOffset());
    }
    else {
      Segment rangeInFile = myPsiFileRange.getRange();
      if (rangeInFile == null) return null;
      result = ProperTextRange.create(rangeInFile);
    }
    int delta = elementRange.getStartOffset();
    return result.getStartOffset() < delta ? null : result.shiftRight(-delta);
  }

  /**
   * Override this method if you want a tooltip to be displayed for this usage
   */
  public String getTooltipText () {
    return null;
  }

  public final void navigateTo(boolean requestFocus) {
    int offset = getNavigationOffset();
    VirtualFile file = getVirtualFile();
    Project project = getProject();
    FileEditorManager.getInstance(project).openTextEditor(new OpenFileDescriptor(project, file, offset), requestFocus);
  }

  public int getNavigationOffset() {
    if (myPsiFileRange  != null) {
      final Segment range = myPsiFileRange.getRange();
      if (range != null) {
        return range.getStartOffset();
      }
    }

    PsiElement element = getElement();
    if (element == null) return -1;
    TextRange range = element.getTextRange();

    TextRange rangeInElement = getRangeInElement();
    if (rangeInElement == null) return -1;
    return range.getStartOffset() + rangeInElement.getStartOffset();
  }

  @Nullable
  public Segment getSegment() {
    PsiElement element = getElement();
    if (element == null) return null;
    TextRange range = element.getTextRange();
    ProperTextRange.assertProperRange(range, element);
    if (element instanceof PsiFile) {
      // hack: it's actually a range inside file, use document for range checking since during the "find|replace all" operation, file range might have been changed
      Document document = PsiDocumentManager.getInstance(getProject()).getDocument((PsiFile)element);
      if (document != null) {
        range = new ProperTextRange(0, document.getTextLength());
      }
    }
    ProperTextRange rangeInElement = getRangeInElement();
    if (rangeInElement == null) return null;
    return new ProperTextRange(Math.min(range.getEndOffset(), range.getStartOffset() + rangeInElement.getStartOffset()),
                               Math.min(range.getEndOffset(), range.getStartOffset() + rangeInElement.getEndOffset()));
  }

  public Project getProject() {
    return mySmartPointer.getProject();
  }

  public final boolean isWritable() {
    PsiElement element = getElement();
    return element == null || element.isWritable();
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (!getClass().equals(o.getClass())) return false;

    final UsageInfo usageInfo = (UsageInfo)o;

    if (isNonCodeUsage != usageInfo.isNonCodeUsage) return false;

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    return smartPointerManager.pointToTheSameElement(mySmartPointer, usageInfo.mySmartPointer)
           && (myPsiFileRange == null || usageInfo.myPsiFileRange != null && smartPointerManager.pointToTheSameElement(myPsiFileRange, usageInfo.myPsiFileRange));
  }

  public int hashCode() {
    int result = mySmartPointer != null ? mySmartPointer.hashCode() : 0;
    result = 29 * result + (myPsiFileRange == null ? 0 : myPsiFileRange.hashCode());
    result = 29 * result + (isNonCodeUsage ? 1 : 0);
    return result;
  }

  @Nullable
  public PsiFile getFile() {
    return mySmartPointer.getContainingFile();
  }

  @Nullable
  public VirtualFile getVirtualFile() {
    return mySmartPointer.getVirtualFile();
  }

  public boolean isDynamicUsage() {
    return myDynamicUsage;
  }
}
