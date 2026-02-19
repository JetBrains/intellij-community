
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.java.JavaBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiDeclarationStatement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementFactory;
import com.intellij.psi.PsiLocalVariable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiReferenceExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public class JavaWithRunnableSurrounder extends JavaStatementsModCommandSurrounder {
  @Override
  public String getTemplateDescription() {
    return JavaBundle.message("surround.with.runnable.template");
  }

  @Override
  protected void surroundStatements(@NotNull ActionContext context,
                                    @NotNull PsiElement container,
                                    @NotNull PsiElement @NotNull [] statements,
                                    @NotNull ModPsiUpdater updater) throws IncorrectOperationException {
    Project project = context.project();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final String baseName = "runnable";
    final String uniqueName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(baseName, container, false);

    @NonNls String text = "Runnable runnable = new Runnable(){\npublic void run(){\n}};";
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)factory.createStatementFromText(text, null);
    declarationStatement = (PsiDeclarationStatement)codeStyleManager.reformat(declarationStatement);

    declarationStatement = (PsiDeclarationStatement)addAfter(declarationStatement, container, statements);

    final PsiVariable variable = (PsiVariable)declarationStatement.getDeclaredElements()[0];

    if (!Comparing.strEqual(uniqueName, baseName)) {
      variable.setName(uniqueName);
    }

    PsiNewExpression newExpression = (PsiNewExpression)variable.getInitializer();
    PsiElement[] children = newExpression.getChildren();
    PsiAnonymousClass anonymousClass = (PsiAnonymousClass)children[children.length - 1];
    PsiMethod method = anonymousClass.getMethods()[0];
    PsiCodeBlock body = method.getBody();
    body.addRange(statements[0], statements[statements.length - 1]);
    container.deleteChildRange(statements[0], statements[statements.length - 1]);

    makeVariablesFinal(body, body);
    updater.rename(variable, List.of(uniqueName));
  }

  private static void makeVariablesFinal(PsiElement scope, PsiCodeBlock body) throws IncorrectOperationException{
    //Q : check if variable may not be final (assigned twice)?
    PsiElement[] children = scope.getChildren();

    for (PsiElement child : children) {
      makeVariablesFinal(child, body);

      if (child instanceof PsiReferenceExpression ref) {
        if (child.getParent() instanceof PsiMethodCallExpression) continue;
        if (PsiUtil.isAccessedForWriting(ref)) {
          continue;
        }

        PsiElement refElement = ref.resolve();
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
            if (parent instanceof PsiMethod method) {
              enclosingMethod = method;
            }
            parent = parent.getParent();
          }
          if ((parent == null) && canBeDeclaredFinal(variable, enclosingMethod)) {
            PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
          }
        }
      }
    }
  }

  private static boolean canBeDeclaredFinal(final @NotNull PsiVariable variable, final @Nullable PsiElement scope) {
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