package com.intellij.codeInspection.ex;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.command.undo.UndoUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class QuickFixWrapper implements IntentionAction {
  private ProblemDescriptor myDescriptor;
  private int myFixNumber;
  private static final Logger LOG = Logger.getInstance("com.intellij.codeInspection.ex.QuickFixWrapper");

  public QuickFixWrapper(@NotNull ProblemDescriptor descriptor, int fixNumber) {
    myDescriptor = descriptor;
    myFixNumber = fixNumber;
    LOG.assertTrue(fixNumber > -1);
    LOG.assertTrue(descriptor.getFixes() != null && descriptor.getFixes().length > fixNumber);
  }

  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @NotNull
  public String getFamilyName() {
    return myDescriptor.getFixes()[myFixNumber].getName();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiElement psiElement = myDescriptor.getPsiElement();
    return psiElement != null && psiElement.isValid();
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    LocalQuickFix fix = (LocalQuickFix)myDescriptor.getFixes()[myFixNumber];
    fix.applyFix(project, myDescriptor);
    DaemonCodeAnalyzer.getInstance(project).restart();
    final PsiElement element = myDescriptor.getPsiElement();
    if (element != null) {
      final PsiFile fileForUndo = element.getContainingFile();
      if (!Comparing.equal(fileForUndo, file)) {
        UndoUtil.markPsiFileForUndo(fileForUndo);
      }
    }
  }

  public boolean startInWriteAction() {
    return true;
  }

  public LocalQuickFix getFix() {
    return (LocalQuickFix)myDescriptor.getFixes()[myFixNumber];
  }
  
  public String toString() {
    return getText();
  }
}
