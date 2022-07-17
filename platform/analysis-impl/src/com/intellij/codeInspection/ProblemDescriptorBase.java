// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection;

import com.intellij.codeInspection.util.InspectionMessage;
import com.intellij.lang.annotation.ProblemGroup;
import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.objectTree.ThrowableInterner;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilCore;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

public class ProblemDescriptorBase extends CommonProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance(ProblemDescriptorBase.class);

  @NotNull private final SmartPsiElementPointer<?> myStartSmartPointer;
  @Nullable private final SmartPsiElementPointer<?> myEndSmartPointer; // null means it's the same as myStartSmartPointer

  private final ProblemHighlightType myHighlightType;
  private Navigatable myNavigatable;
  private final boolean myAfterEndOfLine;
  private final TextRange myTextRangeInElement;
  private final boolean myShowTooltip;
  private TextAttributesKey myEnforcedTextAttributes;
  private int myLineNumber = -1;
  private ProblemGroup myProblemGroup;
  @Nullable private final Throwable myCreationTrace;

  public ProblemDescriptorBase(@NotNull PsiElement startElement,
                               @NotNull PsiElement endElement,
                               @NotNull @InspectionMessage String descriptionTemplate,
                               LocalQuickFix @Nullable [] fixes,
                               @NotNull ProblemHighlightType highlightType,
                               boolean isAfterEndOfLine,
                               @Nullable TextRange rangeInElement,
                               boolean showTooltip,
                               boolean onTheFly) {
    super(filterFixes(fixes, onTheFly), descriptionTemplate);
    myShowTooltip = showTooltip;
    PsiFile startContainingFile = startElement.getContainingFile();
    LOG.assertTrue(startContainingFile != null && startContainingFile.isValid() || startElement.isValid(), startElement);
    PsiFile endContainingFile = startElement == endElement ? startContainingFile : endElement.getContainingFile();
    LOG.assertTrue(startElement == endElement || endContainingFile != null && endContainingFile.isValid() || endElement.isValid(), endElement);
    assertPhysical(startElement);
    if (startElement != endElement) assertPhysical(endElement);

    TextRange startElementRange = getAnnotationRange(startElement);
    LOG.assertTrue(startElement instanceof ExternallyAnnotated || startElementRange != null, startElement);
    TextRange endElementRange = startElement == endElement ? startElementRange : getAnnotationRange(endElement);
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
                  " must not exceed descriptor text range (" + startElementRange.getStartOffset() + ", " + endElementRange.getEndOffset() + ")" +
                  " length ("+(endElementRange.getEndOffset()-startElementRange.getStartOffset())+").");
      }
    }
    if (rangeInElement != null) {
      TextRange.assertProperRange(rangeInElement);
    }

    myHighlightType = highlightType;
    Project project = startContainingFile == null ? startElement.getProject() : startContainingFile.getProject();
    SmartPointerManager manager = SmartPointerManager.getInstance(project);
    myStartSmartPointer = manager.createSmartPsiElementPointer(startElement, startContainingFile);
    myEndSmartPointer = startElement == endElement ? null : manager.createSmartPsiElementPointer(endElement, endContainingFile);
    if (myEndSmartPointer != null && endContainingFile != startContainingFile) {
      LOG.error("start/end elements should be from the same file but was " +
                "startContainingFile="+startContainingFile + " ("+(startContainingFile == null ? null : PsiUtilCore.getVirtualFile(startContainingFile))+"), "+
                "endContainingFile="+endContainingFile + " ("+(endContainingFile == null ? null : PsiUtilCore.getVirtualFile(endContainingFile))+")"
      );
    }

    myAfterEndOfLine = isAfterEndOfLine;
    myTextRangeInElement = rangeInElement;
    myCreationTrace = onTheFly ? null : ThrowableInterner.intern(new Throwable());
  }

  private static LocalQuickFix @Nullable [] filterFixes(LocalQuickFix @Nullable [] fixes, boolean onTheFly) {
    if (onTheFly || fixes == null) return fixes;
    return Stream.of(fixes).filter(fix -> fix != null && fix.availableInBatchMode()).toArray(LocalQuickFix[]::new);
  }

  public boolean isOnTheFly() {
    return myCreationTrace == null;
  }

  @Nullable
  private static TextRange getAnnotationRange(@NotNull PsiElement startElement) {
    return startElement instanceof ExternallyAnnotated
           ? ((ExternallyAnnotated)startElement).getAnnotationRegion()
           : startElement.getTextRange();
  }

  protected void assertPhysical(@NotNull PsiElement element) {
    if (!element.isPhysical()) {
      LOG.error("Non-physical PsiElement. Physical element is required to be able to anchor the problem in the source tree: " +
                element + "; parent: " + element.getParent() +"; file: " + element.getContainingFile());
    }
  }

  @Nullable
  public Throwable getCreationTrace() {
    return myCreationTrace;
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

  @NotNull
  public Project getProject() {
    return myStartSmartPointer.getProject();
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
      int startOffset = textRange.getStartOffset();
      int textLength = document.getTextLength();
      LOG.assertTrue(startOffset <= textLength, getDescriptionTemplate() + " at " + startOffset + ", " + textLength);
      myLineNumber =  document.getLineNumber(startOffset);
    }
    return myLineNumber;
  }

  public int getLineStartOffset() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null || !psiElement.isValid()) return -1;
    InjectedLanguageManager manager = InjectedLanguageManager.getInstance(psiElement.getProject());
    PsiFile containingFile = manager.getTopLevelFile(psiElement);
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(containingFile);
    if (document == null) return -1;
    return document.getLineStartOffset(getLineNumber());
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
      if (myTextRangeInElement.getEndOffset() > startRange.getLength()) {
        // became invalid
        return null;
      }
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

  @Nullable
  public VirtualFile getContainingFile() {
    return myStartSmartPointer.getVirtualFile();
  }

  public void setNavigatable(Navigatable navigatable) {
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

  @Override
  public LocalQuickFix @Nullable [] getFixes() {
    return (LocalQuickFix[])super.getFixes();
  }
}
