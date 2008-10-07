package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class DeleteMethodBodyFix implements IntentionAction {
  private final PsiMethod myMethod;

  public DeleteMethodBodyFix(PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("delete.body.text");
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("delete.body.family");
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myMethod.isValid() && myMethod.getManager().isInProject(myMethod) && myMethod.getBody() != null;
  }

  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtilBase.preparePsiElementForWrite(myMethod)) return;
    final PsiCodeBlock body = myMethod.getBody();
    assert body != null;
    body.delete();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
