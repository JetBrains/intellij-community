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

package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ProblemDescriptorImpl extends CommonProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.ProblemDescriptorImpl");

  @NotNull private final SmartPsiElementPointer myStartSmartPointer;
  @Nullable private final SmartPsiElementPointer myEndSmartPointer;


  private final ProblemHighlightType myHighlightType;
  private Navigatable myNavigatable;
  private final boolean myAfterEndOfLine;
  private final TextRange myTextRangeInElement;
  private final boolean myShowTooltip;
  private final HintAction myHintAction;
  private TextAttributesKey myEnforcedTextAttributes;

  public ProblemDescriptorImpl(@NotNull PsiElement startElement, @NotNull PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType,
                               boolean isAfterEndOfLine,
                               final TextRange rangeInElement,
                               boolean onTheFly) {
    this(startElement, endElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, rangeInElement, null, onTheFly);
  }

  public ProblemDescriptorImpl(@NotNull PsiElement startElement, @NotNull PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType,
                               boolean isAfterEndOfLine,
                               final TextRange rangeInElement,
                               @Nullable HintAction hintAction,
                               boolean onTheFly) {
    this(startElement, endElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, rangeInElement, true, hintAction, onTheFly);
  }

  public ProblemDescriptorImpl(@NotNull PsiElement startElement, @NotNull PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType,
                               boolean isAfterEndOfLine,
                               final TextRange rangeInElement,
                               final boolean tooltip,
                               @Nullable HintAction hintAction,
                               boolean onTheFly) {

    super(fixes, descriptionTemplate);
    myShowTooltip = tooltip;
    myHintAction = hintAction;
    LOG.assertTrue(startElement.isValid(), startElement);
    LOG.assertTrue(startElement == endElement || endElement.isValid(), endElement);
    assertPhysical(startElement);
    if (startElement != endElement) assertPhysical(endElement);

    if (startElement.getTextRange().getStartOffset() >= endElement.getTextRange().getEndOffset()) {
      if (!(startElement instanceof PsiFile && endElement instanceof PsiFile)) {
        LOG.error("Empty PSI elements should not be passed to createDescriptor. Start: " + startElement + ", end: " + endElement);
      }
    }

    myHighlightType = highlightType;
    final Project project = startElement.getProject();
    final boolean useLazy = !onTheFly || ApplicationManager.getApplication().isHeadlessEnvironment();
    final SmartPointerManager manager = SmartPointerManager.getInstance(project);
    myStartSmartPointer = useLazy? manager.createLazyPointer(startElement) : manager.createSmartPsiElementPointer(startElement);
    myEndSmartPointer = startElement == endElement ? null : useLazy ? manager.createLazyPointer(endElement) : manager.createSmartPsiElementPointer(endElement);

    myAfterEndOfLine = isAfterEndOfLine;
    myTextRangeInElement = rangeInElement;
  }

  protected void assertPhysical(final PsiElement element) {
    if (!element.isPhysical()) {
      LOG.error("Non-physical PsiElement. Physical element is required to be able to anchor the problem in the source tree: " +
                element + "; file: " + element.getContainingFile());
    }
  }

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

  public PsiElement getStartElement() {
    return myStartSmartPointer.getElement();
  }

  public PsiElement getEndElement() {
    return myEndSmartPointer == null ? getStartElement() : myEndSmartPointer.getElement();
  }

  public int getLineNumber() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return -1;
    if (!psiElement.isValid()) return -1;
    LOG.assertTrue(psiElement.isPhysical());
    PsiFile containingFile = InjectedLanguageUtil.getTopLevelFile(psiElement);
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(containingFile);
    if (document == null) return -1;
    TextRange textRange = getTextRange();
    if (textRange == null) return -1;
    textRange = InjectedLanguageManager.getInstance(containingFile.getProject()).injectedToHost(psiElement, textRange);
    return document.getLineNumber(textRange.getStartOffset()) + 1;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  public void setTextAttributes(TextAttributesKey key) {
    myEnforcedTextAttributes = key;
  }

  public TextAttributesKey getEnforcedTextAttributes() {
    return myEnforcedTextAttributes;
  }

  public TextRange getTextRangeForNavigation() {
    TextRange textRange = getTextRange();
    if (textRange == null) return null;
    PsiElement element = getPsiElement();
    return InjectedLanguageManager.getInstance(element.getProject()).injectedToHost(element, element.getTextRange());
  }

  public TextRange getTextRange() {
    PsiElement startElement = getStartElement();
    PsiElement endElement = myEndSmartPointer == null ? startElement : getEndElement();
    if (startElement == null || endElement == null) {
      return null;
    }

    TextRange textRange = startElement.getTextRange();
    if (startElement == endElement) {
      if (isAfterEndOfLine()) return new TextRange(textRange.getEndOffset(), textRange.getEndOffset());
      if (myTextRangeInElement != null) {
        return new TextRange(textRange.getStartOffset() + myTextRangeInElement.getStartOffset(),
                             textRange.getStartOffset() + myTextRangeInElement.getEndOffset());
      }
      return textRange;
    }
    return new TextRange(textRange.getStartOffset(), endElement.getTextRange().getEndOffset());
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public void setNavigatable(final Navigatable navigatable) {
    myNavigatable = navigatable;
  }

  public HintAction getHintAction() {
    return myHintAction;
  }

  public boolean showTooltip() {
    return myShowTooltip;
  }
}
