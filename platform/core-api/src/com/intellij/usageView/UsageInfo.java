/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
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
  protected boolean myDynamicUsage;

  public UsageInfo(@NotNull PsiElement originalElement, final int startOffset, final int endOffset, boolean isNonCodeUsage) {
    PsiElement element = originalElement.getNavigationElement();
    PsiFile file = element.getContainingFile();
    PsiElement topElement = file == null ? element : file;
    LOG.assertTrue(topElement.isValid(), element);

    TextRange elementRange = element.getTextRange();
    if (elementRange == null) {
      throw new IllegalArgumentException("text range null for " + element + "; " + element.getClass());
    }
    int effectiveStart;
    int effectiveEnd;
    if (startOffset == -1 && endOffset == -1) {
      // calculate natural element range
      // Cls element.getTextOffset() returns -1
      effectiveStart = Math.max(0, element.getTextOffset() - elementRange.getStartOffset());
      effectiveEnd = Math.max(effectiveStart, elementRange.getLength());
    }
    else {
      effectiveStart = startOffset;
      effectiveEnd = endOffset;
      if (element != originalElement) {
        PsiFile originalFile = originalElement.getContainingFile();
        if (originalFile == file) {
          int delta = originalElement.getTextRange().getStartOffset() - elementRange.getStartOffset();
          effectiveStart += delta;
          effectiveEnd += delta;
        }
        else {
          throw new IllegalArgumentException("element.getNavigationElement() for element "+originalElement+"("+startOffset+", "+endOffset+
               ") from " + originalFile + " led to different file "+file+
               ", thus making passed offsets invalid. Specify -1 for start/end offsets to calculate correct offsets for navigation.");
        }
      }
    }

    if (effectiveStart < 0 || effectiveStart > effectiveEnd) {
      throw new IllegalArgumentException("element " + element + "; startOffset " +startOffset+"; endOffset="+endOffset+
                                         "; effectiveStart="+effectiveStart+"; effectiveEnd="+effectiveEnd+
                                         "; elementRange="+elementRange+"; element.getTextOffset()="+element.getTextOffset());
    }

    Project project = topElement.getProject();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    mySmartPointer = smartPointerManager.createSmartPsiElementPointer(element, file);
    if (file != null &&
        (effectiveStart != element.getTextOffset() - elementRange.getStartOffset() || effectiveEnd != elementRange.getLength())) {
      TextRange rangeToStore = InjectedLanguageManager.getInstance(project).isInjectedFragment(file)
                               ? elementRange
                               : TextRange.create(effectiveStart, effectiveEnd).shiftRight(elementRange.getStartOffset());
      myPsiFileRange = smartPointerManager.createSmartPsiFileRangePointer(file, rangeToStore);
    }
    else {
      myPsiFileRange = null;
    }
    this.isNonCodeUsage = isNonCodeUsage;
  }

  public UsageInfo(@NotNull SmartPsiElementPointer<?> smartPointer,
                   @Nullable SmartPsiFileRange psiFileRange,
                   boolean dynamicUsage,
                   boolean nonCodeUsage) {
    myDynamicUsage = dynamicUsage;
    isNonCodeUsage = nonCodeUsage;
    myPsiFileRange = psiFileRange;
    mySmartPointer = smartPointer;
  }

  // in case of find file by name, not by text inside. Since it can be a binary file, do not query for text offsets.
  public UsageInfo(@NotNull PsiFile psiFile) {
    Project project = psiFile.getProject();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(project);
    mySmartPointer = smartPointerManager.createSmartPsiElementPointer(psiFile);
    myPsiFileRange = null;
    isNonCodeUsage = true;
  }

  public UsageInfo(@NotNull PsiElement element, boolean isNonCodeUsage) {
    this(element, -1, -1, isNonCodeUsage);
  }

  public UsageInfo(@NotNull PsiElement element, int startOffset, int endOffset) {
    this(element, startOffset, endOffset, false);
  }

  public UsageInfo(@NotNull PsiReference reference) {
    this(reference.getElement(), reference.getRangeInElement().getStartOffset(), reference.getRangeInElement().getEndOffset());
    if (reference instanceof PsiPolyVariantReference) {
      myDynamicUsage = ((PsiPolyVariantReference)reference).multiResolve(false).length == 0;
    }
    else {
      myDynamicUsage = reference.resolve() == null;
    }
  }

  public UsageInfo(@NotNull PsiQualifiedReferenceElement reference) {
    this((PsiElement)reference);
  }

  public UsageInfo(@NotNull PsiElement element) {
    this(element, false);
  }

  @NotNull
  public SmartPsiElementPointer<?> getSmartPointer() {
    return mySmartPointer;
  }

  public SmartPsiFileRange getPsiFileRange() {
    return myPsiFileRange;
  }

  public boolean isNonCodeUsage() {
    return isNonCodeUsage;
  }

  public void setDynamicUsage(boolean dynamicUsage) {
    myDynamicUsage = dynamicUsage;
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
  public String getTooltipText() {
    return null;
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

  public Segment getNavigationRange() {
    if (myPsiFileRange  != null) {
      final Segment range = myPsiFileRange.getRange();
      if (range != null) {
        return range;
      }
    }

    PsiElement element = getElement();
    if (element == null) return null;
    TextRange range = element.getTextRange();

    TextRange rangeInElement = getRangeInElement();
    if (rangeInElement == null) return null;
    return rangeInElement.shiftRight(range.getStartOffset());
  }

  public boolean isValid() {
    if (isFileUsage()) {
      return true; // in case of binary file
    }
    return getSegment() != null;
  }

  protected boolean isFileUsage() {
    return myPsiFileRange == null && getElement() instanceof PsiFile;
  }

  @Nullable
  public Segment getSegment() {
    PsiElement element = getElement();
    if (element == null
        // in case of binary file
        || myPsiFileRange == null && element instanceof PsiFile) return null;
    TextRange range = element.getTextRange();
    TextRange.assertProperRange(range, element);
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

  private Pair<VirtualFile, Integer> offset() {
    VirtualFile containingFile0 = getVirtualFile();
    int shift0 = 0;
    if (containingFile0 instanceof VirtualFileWindow) {
      shift0 = ((VirtualFileWindow)containingFile0).getDocumentWindow().injectedToHost(0);
      containingFile0 = ((VirtualFileWindow)containingFile0).getDelegate();
    }
    Segment range = myPsiFileRange == null ? mySmartPointer.getPsiRange() : myPsiFileRange.getPsiRange();
    if (range == null) return null;
    return Pair.create(containingFile0, range.getStartOffset() + shift0);
  }

  public int compareToByStartOffset(@NotNull UsageInfo info) {
    Pair<VirtualFile, Integer> offset0 = offset();
    Pair<VirtualFile, Integer> offset1 = info.offset();
    if (offset0 == null || offset0.first == null || offset1 == null || offset1.first == null || !Comparing.equal(offset0.first, offset1.first)) {
      return 0;
    }
    return offset0.second - offset1.second;
  }

  @NotNull
  public Project getProject() {
    return mySmartPointer.getProject();
  }

  public final boolean isWritable() {
    PsiElement element = getElement();
    return element == null || element.isWritable();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!getClass().equals(o.getClass())) return false;

    final UsageInfo usageInfo = (UsageInfo)o;

    if (isNonCodeUsage != usageInfo.isNonCodeUsage) return false;

    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    return smartPointerManager.pointToTheSameElement(mySmartPointer, usageInfo.mySmartPointer)
           && (myPsiFileRange == null || usageInfo.myPsiFileRange != null && smartPointerManager.pointToTheSameElement(myPsiFileRange, usageInfo.myPsiFileRange));
  }

  @Override
  public int hashCode() {
    int result = mySmartPointer != null ? mySmartPointer.hashCode() : 0;
    result = 29 * result + (myPsiFileRange == null ? 0 : myPsiFileRange.hashCode());
    result = 29 * result + (isNonCodeUsage ? 1 : 0);
    return result;
  }

  @Override
  public String toString() {
    PsiReference reference = getReference();
    if (reference == null) {
      return super.toString();
    }
    return reference.getCanonicalText() + " (" + reference.getClass() + ")";
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

  // creates new smart pointers
  @Nullable("null means could not copy because info is no longer valid")
  public UsageInfo copy() {
    PsiElement element = mySmartPointer.getElement();
    SmartPointerManager smartPointerManager = SmartPointerManager.getInstance(getProject());
    PsiFile containingFile = myPsiFileRange == null ? null : myPsiFileRange.getContainingFile();
    Segment segment = containingFile == null ? null : myPsiFileRange.getRange();
    TextRange range = segment == null ? null : TextRange.create(segment);
    SmartPsiFileRange psiFileRange = range == null ? null : smartPointerManager.createSmartPsiFileRangePointer(containingFile, range);
    SmartPsiElementPointer<PsiElement> pointer = element == null || !isValid() ? null : smartPointerManager.createSmartPsiElementPointer(element);
    return pointer == null ? null : new UsageInfo(pointer, psiFileRange, isDynamicUsage(), isNonCodeUsage());
  }
}
