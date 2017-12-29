/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ProblemDescriptorBase extends CommonProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.ProblemDescriptorImpl");

  @NotNull private final SmartPsiElementPointer myStartSmartPointer;
  @Nullable private final SmartPsiElementPointer myEndSmartPointer;

  private final ProblemHighlightType myHighlightType;
  private Navigatable myNavigatable;
  private final boolean myAfterEndOfLine;
  private final TextRange myTextRangeInElement;
  private final boolean myShowTooltip;
  private TextAttributesKey myEnforcedTextAttributes;
  private int myLineNumber = -1;
  private ProblemGroup myProblemGroup;

  public ProblemDescriptorBase(@NotNull PsiElement startElement,
                               @NotNull PsiElement endElement,
                               @NotNull String descriptionTemplate,
                               LocalQuickFix[] fixes,
                               @NotNull ProblemHighlightType highlightType,
                               boolean isAfterEndOfLine,
                               @Nullable TextRange rangeInElement,
                               final boolean showTooltip,
                               boolean onTheFly) {
    super(fixes, descriptionTemplate);
    myShowTooltip = showTooltip;
    PsiFile startContainingFile = startElement.getContainingFile();
    LOG.assertTrue(startContainingFile != null && startContainingFile.isValid() || startElement.isValid(), startElement);
    PsiFile endContainingFile = startElement == endElement ? startContainingFile : endElement.getContainingFile();
    LOG.assertTrue(startElement == endElement || endContainingFile != null && endContainingFile.isValid() || endElement.isValid(), endElement);
    assertPhysical(startElement);
    if (startElement != endElement) assertPhysical(endElement);

    final TextRange startElementRange = getAnnotationRange(startElement);
    LOG.assertTrue(startElement instanceof ExternallyAnnotated || startElementRange != null, startElement);
    final TextRange endElementRange = getAnnotationRange(endElement);
    LOG.assertTrue(endElement instanceof ExternallyAnnotated || endElementRange != null, endElement);
    if (startElementRange != null
        && endElementRange != null
        && startElementRange.getStartOffset() >= endElementRange.getEndOffset()) {
      if (!(startElement instanceof PsiFile && endElement instanceof PsiFile)) {
        LOG.error("Empty PSI elements must not be passed to createDescriptor. Start: " + startElement + ", end: " + endElement + ", startContainingFile: " + startContainingFile);
      }
    }
    if (rangeInElement != null && startElementRange != null && endElementRange != null) {
      TextRange.assertProperRange(rangeInElement);
      if (rangeInElement.getEndOffset() > endElementRange.getEndOffset() - startElementRange.getStartOffset()) {
        LOG.error("Argument rangeInElement " + rangeInElement + " endOffset"+
                  " must not exceed descriptor text range " +
                  "(" + startElementRange.getStartOffset() +
                  ", " + endElementRange.getEndOffset() + ")" +
                  " length ("+(endElementRange.getEndOffset()-startElementRange.getStartOffset())+").");
      }
    }

    myHighlightType = highlightType;
    final Project project = startContainingFile == null ? startElement.getProject() : startContainingFile.getProject();
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    myStartSmartPointer = manager.createSmartPsiElementPointer(startElement, startContainingFile);
    myEndSmartPointer = startElement == endElement ? null : manager.createSmartPsiElementPointer(endElement, endContainingFile);

    myAfterEndOfLine = isAfterEndOfLine;
    myTextRangeInElement = rangeInElement;
  }

  @Nullable
  private static TextRange getAnnotationRange(@NotNull PsiElement startElement) {
    return startElement instanceof ExternallyAnnotated
           ? ((ExternallyAnnotated)startElement).getAnnotationRegion()
           : startElement.getTextRange();
  }

  protected void assertPhysical(final PsiElement element) {
    if (!element.isPhysical()) {
      LOG.error("Non-physical PsiElement. Physical element is required to be able to anchor the problem in the source tree: " +
                element + "; file: " + element.getContainingFile());
    }
  }

  @Override
  public PsiElement getPsiElement() {
    PsiElement startElement = getStartElement();
    if (myEndSmartPointer == null) {
      return startElement;
    }
    PsiElement endElement = getEndElement();
    if (startElement == endElement) {
      return startElement;
    }
    if (startElement == null || endElement == null) return null;
    return PsiTreeUtil.findCommonParent(startElement, endElement);
  }

  @Override
  @Nullable
  public TextRange getTextRangeInElement() {
    return myTextRangeInElement;
  }

  @Override
  public PsiElement getStartElement() {
    return myStartSmartPointer.getElement();
  }

  @Override
  public PsiElement getEndElement() {
    return myEndSmartPointer == null ? getStartElement() : myEndSmartPointer.getElement();
  }

  @Override
  public int getLineNumber() {
    if (myLineNumber == -1) {
      PsiElement psiElement = getPsiElement();
      if (psiElement == null) return -1;
      if (!psiElement.isValid()) return -1;
      LOG.assertTrue(psiElement.isPhysical());
      InjectedLanguageManager manager = InjectedLanguageManager.getInstance(psiElement.getProject());
      PsiFile containingFile = manager.getTopLevelFile(psiElement);
      Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(containingFile);
      if (document == null) return -1;
      TextRange textRange = getTextRange();
      if (textRange == null) return -1;
      textRange = manager.injectedToHost(psiElement, textRange);
      final int startOffset = textRange.getStartOffset();
      final int textLength = document.getTextLength();
      LOG.assertTrue(startOffset <= textLength, getDescriptionTemplate() + " at " + startOffset + ", " + textLength);
      myLineNumber =  document.getLineNumber(startOffset);
    }
    return myLineNumber;
  }

  @NotNull
  @Override
  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  @Override
  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  @Override
  public void setTextAttributes(TextAttributesKey key) {
    myEnforcedTextAttributes = key;
  }

  public TextAttributesKey getEnforcedTextAttributes() {
    return myEnforcedTextAttributes;
  }

  @Nullable
  public TextRange getTextRangeForNavigation() {
    TextRange textRange = getTextRange();
    if (textRange == null) return null;
    PsiElement element = getPsiElement();
    return InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, textRange);
  }

  @Nullable
  public TextRange getTextRange() {
    PsiElement startElement = getStartElement();
    PsiElement endElement = myEndSmartPointer == null ? startElement : getEndElement();
    if (startElement == null || endElement == null) {
      return null;
    }

    TextRange startRange = getAnnotationRange(startElement);
    if (startRange == null) {
      return null;
    }

    if (startElement != endElement) {
      TextRange endRange = getAnnotationRange(endElement);
      if (endRange == null) return null;
      startRange = startRange.union(endRange);
    }
    else if (myTextRangeInElement != null) {
      startRange = startRange.cutOut(myTextRangeInElement);
    }
    if (isAfterEndOfLine()) {
      int endOffset = startRange.getEndOffset();
      return new TextRange(endOffset, endOffset);
    }
    return startRange;
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public void setNavigatable(final Navigatable navigatable) {
    myNavigatable = navigatable;
  }

  @Override
  @Nullable
  public ProblemGroup getProblemGroup() {
    return myProblemGroup;
  }

  @Override
  public void setProblemGroup(@Nullable ProblemGroup problemGroup) {
    myProblemGroup = problemGroup;
  }

  @Override
  public boolean showTooltip() {
    return myShowTooltip;
  }

  @Override
  public String toString() {
    PsiElement element = getPsiElement();
    return ProblemDescriptorUtil.renderDescriptionMessage(this, element);
  }
}
