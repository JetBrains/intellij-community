package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
public class ProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.ProblemDescriptorImpl");

  @NotNull private SmartPsiElementPointer myStartSmartPointer;
  @Nullable private SmartPsiElementPointer myEndSmartPointer;
  private final String myDescriptionTemplate;
  private final LocalQuickFix[] myFixes;
  private ProblemHighlightType myHighlightType;
  private boolean myAfterEndOfLine;

  public ProblemDescriptorImpl(PsiElement startElement, PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes, ProblemHighlightType highlightType, boolean isAfterEndOfLine) {
    LOG.assertTrue(startElement.isValid());
    LOG.assertTrue(startElement.isPhysical());
    LOG.assertTrue(endElement.isValid());
    LOG.assertTrue(endElement.isPhysical());

    if (startElement.getTextRange().getStartOffset() >= endElement.getTextRange().getEndOffset()) {
      LOG.error("Empty PSI elements should not be passed to createDescriptor");
    }

    if (fixes != null) {
      myFixes = new LocalQuickFix[fixes.length];
      System.arraycopy(fixes, 0, myFixes, 0, fixes.length);
    } else {
      myFixes = null;
    }

    myHighlightType = highlightType;
    final Project project = startElement.getProject();
    myStartSmartPointer = SmartPointerManager.getInstance(project).createLazyPointer(startElement);
    myEndSmartPointer = startElement == endElement ? null : SmartPointerManager.getInstance(project).createLazyPointer(endElement);
    myDescriptionTemplate = descriptionTemplate;
    myAfterEndOfLine = isAfterEndOfLine;
  }

  public PsiElement getPsiElement() {
    PsiElement startElement = getStartElement();
    PsiElement endElement = getEndElement();
    if (startElement == endElement) {
      return startElement;
    }
    if (startElement == null || endElement == null) return null;
    return PsiTreeUtil.findCommonParent(startElement,endElement);
  }

  public PsiElement getStartElement() {
    return myStartSmartPointer.getElement();
  }

  public PsiElement getEndElement() {
    return myEndSmartPointer == null ? myStartSmartPointer.getElement() : myEndSmartPointer.getElement();
  }

  public int getLineNumber() {
    PsiElement psiElement = getPsiElement();
    if (psiElement == null) return -1;
    LOG.assertTrue(psiElement.isPhysical());
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiElement.getContainingFile());
    if (document == null) return -1;
    return document.getLineNumber(psiElement.getTextOffset()) + 1;
  }

  public LocalQuickFix[] getFixes() {
    return myFixes;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  public String getDescriptionTemplate() {
    return myDescriptionTemplate;
  }

  public TextRange getTextRange() {
    PsiElement startElement = getStartElement();
    PsiElement endElement = getEndElement();
    if (startElement == null || endElement == null) return null;
    if (startElement == endElement) return startElement.getTextRange();
    return new TextRange(startElement.getTextRange().getStartOffset(), endElement.getTextRange().getEndOffset());
  }
}
