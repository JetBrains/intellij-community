package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.scope.processor.VariablesNotProcessor;
import com.intellij.psi.scope.util.PsiScopesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

/**
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Nov 20, 2002
 * Time: 3:01:25 PM
 * To change this template use Options | File Templates.
 */
public class ReuseVariableDeclarationFix implements IntentionAction {
  private final PsiVariable variable;
  private final PsiIdentifier identifier;

  public ReuseVariableDeclarationFix(PsiVariable variable, PsiIdentifier identifier) {
    this.variable = variable;
    this.identifier = identifier;
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("reuse.variable.declaration.family");
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("reuse.variable.declaration.text", variable.getName());
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {

    PsiVariable previousVariable = findPreviousVariable();
    return
        variable != null
        && variable.isValid()
        && variable instanceof PsiLocalVariable
        && previousVariable != null
        && Comparing.equal(previousVariable.getType(), variable.getType())
        && identifier != null
        && identifier.isValid()
        && variable.getManager().isInProject(variable);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiVariable refVariable = findPreviousVariable();
    if (refVariable == null) return;
    if (!CodeInsightUtil.preparePsiElementsForWrite(Arrays.asList(variable, refVariable))) return;
    refVariable.getModifierList().setModifierProperty(PsiModifier.FINAL, false);
    if (variable.getInitializer() == null)  {
      variable.delete();
      return;
    }
    PsiDeclarationStatement declaration = (PsiDeclarationStatement) variable.getParent();
    PsiElementFactory factory = JavaPsiFacade.getInstance(variable.getProject()).getElementFactory();
    PsiStatement statement = factory.createStatementFromText(variable.getName() + " = " + variable.getInitializer().getText()+";", variable);
    declaration.replace(statement);
  }

  private PsiVariable findPreviousVariable() {
    PsiElement scope = variable.getParent();
    while (scope != null) {
      if (scope instanceof PsiFile || scope instanceof PsiMethod || scope instanceof PsiClassInitializer) break;
      scope = scope.getParent();
    }
    if (scope == null) return null;
    VariablesNotProcessor proc = new VariablesNotProcessor(variable, false);
    PsiScopesUtil.treeWalkUp(proc, identifier, scope);

    if(proc.size() > 0)
      return proc.getResult(0);
    return null;
  }

  public boolean startInWriteAction() {
    return true;
  }

}
