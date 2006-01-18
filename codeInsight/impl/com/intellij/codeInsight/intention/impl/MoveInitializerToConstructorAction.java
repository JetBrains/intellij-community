package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

import java.util.*;

/**
 * @author cdr
 */
public class MoveInitializerToConstructorAction extends BaseIntentionAction {
  public String getFamilyName() {
    return getText();
  }

  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.constructor");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field == null) return false;
    if (!field.hasInitializer()) return false;
    PsiClass psiClass = field.getContainingClass();
    
    return psiClass != null && !psiClass.isInterface();
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    int offset = editor.getCaretModel().getOffset();

    PsiElement element = file.findElementAt(offset);
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);

    PsiClass aClass = field.getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    Collection<PsiMethod> constructorsToAddInitialization;
    if (constructors.length == 0) {
      IntentionAction addDefaultConstructorFix = QuickFixFactory.getInstance().createAddDefaultConstructorFix(aClass);
      addDefaultConstructorFix.invoke(project, editor, file);
      constructorsToAddInitialization = Arrays.asList(aClass.getConstructors());
    }
    else {
      constructorsToAddInitialization = new ArrayList<PsiMethod>(Arrays.asList(constructors));
      for (Iterator<PsiMethod> iterator = constructorsToAddInitialization.iterator(); iterator.hasNext();) {
        PsiMethod ctr = iterator.next();
        List<PsiMethod> chained = HighlightControlFlowUtil.getChainedConstructors(ctr);
        if (chained != null) {
          iterator.remove();
        }
      }
    }

    for (PsiMethod constructor : constructorsToAddInitialization) {
      PsiCodeBlock codeBlock = constructor.getBody();
      addAssignment(codeBlock, field);
    }
    field.getInitializer().delete();
  }

  private static void addAssignment(final PsiCodeBlock codeBlock, final PsiField field) throws IncorrectOperationException {
    PsiElementFactory factory = codeBlock.getManager().getElementFactory();
    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(field.getName()+" = y;", codeBlock);
    PsiAssignmentExpression expression = (PsiAssignmentExpression)statement.getExpression();
    expression.getRExpression().replace(field.getInitializer());
    PsiElement newStatement = codeBlock.add(statement);
    replaceWithQualifiedReferences(newStatement);
  }

  private static void replaceWithQualifiedReferences(final PsiElement expression) throws IncorrectOperationException {
    PsiReference reference = expression.getReference();
    if (reference != null) {
      PsiElement resolved = reference.resolve();
      if (resolved instanceof PsiVariable && !(resolved instanceof PsiField)) {
        PsiVariable variable = (PsiVariable)resolved;
        PsiElementFactory factory = resolved.getManager().getElementFactory();
        PsiElement qualifiedExpr = factory.createExpressionFromText("this." + variable.getName(), expression);
        expression.replace(qualifiedExpr);
      }
    }
    else {
      for (PsiElement child : expression.getChildren()) {
        replaceWithQualifiedReferences(child);
      }
    }
  }
}
