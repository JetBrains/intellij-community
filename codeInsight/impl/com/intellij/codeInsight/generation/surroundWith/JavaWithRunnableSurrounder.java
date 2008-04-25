
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class JavaWithRunnableSurrounder extends JavaStatementsSurrounder{
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.runnable.template");
  }

  public TextRange surroundStatements(Project project, Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = container.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    @NonNls String text = "Runnable runnable = new Runnable(){\npublic void run(){\n}};";
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)factory.createStatementFromText(text, null);
    declarationStatement = (PsiDeclarationStatement)codeStyleManager.reformat(declarationStatement);

    declarationStatement = (PsiDeclarationStatement)container.addAfter(declarationStatement, statements[statements.length - 1]);

    PsiVariable variable = (PsiVariable)declarationStatement.getDeclaredElements()[0];
    PsiNewExpression newExpression = (PsiNewExpression)variable.getInitializer();
    PsiElement[] children = newExpression.getChildren();
    PsiAnonymousClass anonymousClass = (PsiAnonymousClass)children[children.length - 1];
    PsiMethod method = anonymousClass.getMethods()[0];
    PsiCodeBlock body = method.getBody();
    body.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    makeVariablesFinal(body, body);

    TextRange range = variable.getNameIdentifier().getTextRange();
    return range;
  }

  private static void makeVariablesFinal(PsiElement scope, PsiCodeBlock body) throws IncorrectOperationException{
    //Q : check if variable may not be final (assigned twice)?
    PsiElement[] children = scope.getChildren();

    for (PsiElement child : children) {
      makeVariablesFinal(child, body);

      if (child instanceof PsiReferenceExpression) {
        if (child.getParent() instanceof PsiMethodCallExpression) continue;
        if (PsiUtil.isAccessedForWriting((PsiReferenceExpression) child)) {
          continue;
        }

        PsiElement refElement = ((PsiReferenceExpression)child).resolve();
        if (refElement instanceof PsiLocalVariable || refElement instanceof PsiParameter) {
          PsiVariable variable = (PsiVariable) refElement;
          final PsiModifierList modifierList = variable.getModifierList();
          if ((modifierList != null) && (modifierList.hasModifierProperty(PsiModifier.FINAL))) {
            continue;
          }

          PsiElement parent = variable.getParent();
          PsiMethod enclosingMethod = null;

          while (parent != null) {
            if (parent.equals(body)) break;
            if (parent instanceof PsiMethod) {
              enclosingMethod = (PsiMethod) parent;
            }
            parent = parent.getParent();
          }
          if ((parent == null) && canBeDeclaredFinal(variable, enclosingMethod)) {
            variable.getModifierList().setModifierProperty(PsiModifier.FINAL, true);
          }
        }
      }
    }
  }

  private static boolean canBeDeclaredFinal(@NotNull final PsiVariable variable, @Nullable final PsiElement scope) {
    if (scope == null) {
      return false;
    }
    final Collection<PsiReference> references = ReferencesSearch.search(variable, new LocalSearchScope(scope)).findAll();
    boolean foundOnce = (variable instanceof PsiParameter) || (variable.getInitializer() != null);
    for (PsiReference reference : references) {
      if (reference instanceof PsiReferenceExpression) {
        if (PsiUtil.isAccessedForWriting((PsiReferenceExpression) reference)) {
          if (foundOnce) {
            return false;
          }
          foundOnce = true;
        }
      }
    }
    return true;
  }
}