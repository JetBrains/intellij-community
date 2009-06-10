package com.intellij.codeInspection.ex;

import com.intellij.codeInspection.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.pom.Navigatable;
import com.intellij.psi.*;
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
  private final HintAction myHintAction;

  public ProblemDescriptorImpl(@NotNull PsiElement startElement, @NotNull PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType, boolean isAfterEndOfLine, final TextRange rangeInElement) {
    this(startElement, endElement, descriptionTemplate, fixes, highlightType, isAfterEndOfLine, rangeInElement, null);
  }

  public ProblemDescriptorImpl(@NotNull PsiElement startElement, @NotNull PsiElement endElement, String descriptionTemplate, LocalQuickFix[] fixes,
                               ProblemHighlightType highlightType, boolean isAfterEndOfLine, final TextRange rangeInElement,
                               @Nullable HintAction hintAction) {

    super(fixes, descriptionTemplate);
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
    final boolean useLazy = ApplicationManager.getApplication().isHeadlessEnvironment();
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
    Document document = PsiDocumentManager.getInstance(psiElement.getProject()).getDocument(psiElement.getContainingFile());
    if (document == null) return -1;
    final TextRange textRange = getTextRange();
    if (textRange == null) return -1;
    return document.getLineNumber(textRange.getStartOffset()) + 1;
  }

  public ProblemHighlightType getHighlightType() {
    return myHighlightType;
  }

  public boolean isAfterEndOfLine() {
    return myAfterEndOfLine;
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
}
