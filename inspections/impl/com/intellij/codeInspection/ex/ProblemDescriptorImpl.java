package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.CommonProblemDescriptorImpl;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.Navigatable;
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
public class ProblemDescriptorImpl extends CommonProblemDescriptorImpl implements ProblemDescriptor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.ex.ProblemDescriptorImpl");

  @NotNull private SmartPsiElementPointer myStartSmartPointer;
  @Nullable private SmartPsiElementPointer myEndSmartPointer;


  private ProblemHighlightType myHighlightType;
  private Navigatable myNavigatable;
  private boolean myAfterEndOfLine;
  private TextRange myTextRangeInElement;

  public ProblemDescriptorImpl(PsiElement startElement, PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType, boolean isAfterEndOfLine, final TextRange rangeInElement) {
    super(fixes, descriptionTemplate);
    LOG.assertTrue(startElement.isValid(), "Invalid PsiElement");
    LOG.assertTrue(startElement.isPhysical(), "Non-physical PsiElement. Physical element is required to be able to anchor the problem in the source tree");
    LOG.assertTrue(endElement.isValid(), "Invalid PsiElement");
    LOG.assertTrue(endElement.isPhysical(), "Non-physical PsiElement. Physical element is required to be able to anchor the problem in the source tree");

    if (startElement.getTextRange().getStartOffset() >= endElement.getTextRange().getEndOffset()) {
      LOG.error("Empty PSI elements should not be passed to createDescriptor");
    }

    myHighlightType = highlightType;
    final Project project = startElement.getProject();
    myStartSmartPointer = SmartPointerManager.getInstance(project).createLazyPointer(startElement);
    myEndSmartPointer = startElement == endElement ? null : SmartPointerManager.getInstance(project).createLazyPointer(endElement);

    myAfterEndOfLine = isAfterEndOfLine;
    myTextRangeInElement = rangeInElement;
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
    LOG.assertTrue(psiElement.isPhysical());
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiElement.getContainingFile());
    if (document == null) return -1;
    return document.getLineNumber(psiElement.getTextOffset()) + 1;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
  }

  public TextRange getTextRange() {
    PsiElement startElement = getStartElement();
    PsiElement endElement = getEndElement();
    if (startElement == null || endElement == null) return null;
    TextRange textRange = startElement.getTextRange();
    if (startElement == endElement) {
      if (isAfterEndOfLine()) return new TextRange(textRange.getEndOffset()-1, textRange.getEndOffset());
      return textRange;
    }
    LOG.assertTrue(!isAfterEndOfLine());
    if (myTextRangeInElement != null) {
      return new TextRange(textRange.getStartOffset() + myTextRangeInElement.getStartOffset(),
                           textRange.getStartOffset() + myTextRangeInElement.getEndOffset());
    }
    return new TextRange(textRange.getStartOffset(), endElement.getTextRange().getEndOffset());
  }

  public Navigatable getNavigatable() {
    return myNavigatable;
  }

  public void setNavigatable(final Navigatable navigatable) {
    myNavigatable = navigatable;
  }
}
