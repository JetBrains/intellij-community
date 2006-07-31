package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author cdr
 */
public class MoveInitializerToConstructorAction extends BaseIntentionAction {
  @NotNull
  public String getFamilyName() {
    return getText();
  }

  @NotNull
  public String getText() {
    return CodeInsightBundle.message("intention.move.initializer.to.constructor");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return false;
    if (element instanceof PsiCompiledElement) return false;
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class, false, PsiMember.class, PsiCodeBlock.class, PsiDocComment.class);
    if (field == null || field.hasModifierProperty(PsiModifier.STATIC)) return false;
    if (!field.hasInitializer()) return false;
    PsiClass psiClass = field.getContainingClass();
    
    return psiClass != null && !psiClass.isInterface() && !(psiClass instanceof PsiAnonymousClass);
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    int offset = editor.getCaretModel().getOffset();

    PsiElement element = file.findElementAt(offset);
    final PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);

    assert field != null;
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

    PsiElement toMove = null;
    for (PsiMethod constructor : constructorsToAddInitialization) {
      PsiCodeBlock codeBlock = constructor.getBody();
      PsiElement added = addAssignment(codeBlock, field);
      if (toMove == null) toMove = added;
    }
    field.getInitializer().delete();
    if (toMove != null) {
      editor.getCaretModel().moveToOffset(toMove.getTextOffset());
      editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
    }
  }

  private static PsiElement addAssignment(final PsiCodeBlock codeBlock, final PsiField field) throws IncorrectOperationException {
    PsiElementFactory factory = codeBlock.getManager().getElementFactory();
    PsiExpressionStatement statement = (PsiExpressionStatement)factory.createStatementFromText(field.getName()+" = y;", codeBlock);
    PsiAssignmentExpression expression = (PsiAssignmentExpression)statement.getExpression();
    expression.getRExpression().replace(field.getInitializer());
    PsiStatement[] statements = codeBlock.getStatements();
    PsiElement anchor = null;
    for (PsiStatement blockStatement : statements) {
       if (blockStatement instanceof PsiExpressionStatement && HighlightUtil.isSuperOrThisMethodCall(
         ((PsiExpressionStatement)blockStatement).getExpression())) continue;
      if (containsReference(blockStatement, field)) {
        anchor = blockStatement;
        break;
      }
    }
    PsiElement newStatement = codeBlock.addBefore(statement,anchor);
    replaceWithQualifiedReferences(newStatement);
    return newStatement;
  }

  private static boolean containsReference(final PsiElement element, final PsiField field) {
    final Ref<Boolean> result = new Ref<Boolean>(Boolean.FALSE);
    element.accept(new PsiRecursiveElementVisitor() {
      public void visitReferenceExpression(PsiReferenceExpression expression) {
        if (expression.resolve() == field) {
           result.set(Boolean.TRUE);
        }
        super.visitReferenceExpression(expression);
      }
    });
    return result.get().booleanValue();
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
