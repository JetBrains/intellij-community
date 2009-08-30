package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.editor.Editor;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 */
public class RemoveQualifierFix implements IntentionAction {
  private final PsiExpression myQualifier;
  private final PsiReferenceExpression myExpression;
  private final PsiClass myResolved;

  public RemoveQualifierFix(final PsiExpression qualifier, final PsiReferenceExpression expression, final PsiClass resolved) {
    myQualifier = qualifier;
    myExpression = expression;
    myResolved = resolved;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("remove.qualifier.action.text");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return
      myQualifier != null
      && myQualifier.isValid()
      && myQualifier.getManager().isInProject(myQualifier)
      && myExpression != null
      && myExpression.isValid()
      && myResolved != null
      && myResolved.isValid()
      ;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.prepareFileForWrite(file)) return;
    myQualifier.delete();
    myExpression.bindToElement(myResolved);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
