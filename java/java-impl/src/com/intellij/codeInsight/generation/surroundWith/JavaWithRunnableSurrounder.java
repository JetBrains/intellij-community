
/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.generation.surroundWith;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.rename.inplace.VariableInplaceRenamer;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class JavaWithRunnableSurrounder extends JavaStatementsSurrounder{
  @Override
  public String getTemplateDescription() {
    return CodeInsightBundle.message("surround.with.runnable.template");
  }

  @Override
  public TextRange surroundStatements(Project project, final Editor editor, PsiElement container, PsiElement[] statements) throws IncorrectOperationException{
    PsiManager manager = container.getManager();
    PsiElementFactory factory = JavaPsiFacade.getInstance(manager.getProject()).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    final String baseName = "runnable";
    final String uniqueName = JavaCodeStyleManager.getInstance(project).suggestUniqueVariableName(baseName, container, false);

    @NonNls String text = "Runnable runnable = new Runnable(){\npublic void run(){\n}};";
    PsiDeclarationStatement declarationStatement = (PsiDeclarationStatement)factory.createStatementFromText(text, null);
    declarationStatement = (PsiDeclarationStatement)codeStyleManager.reformat(declarationStatement);

    declarationStatement = (PsiDeclarationStatement)container.addAfter(declarationStatement, statements[statements.length - 1]);

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

    final int textOffset = variable.getNameIdentifier().getTextOffset();
    PsiDocumentManager.getInstance(project).doPostponedOperationsAndUnblockDocument(editor.getDocument());
    editor.getCaretModel().moveToOffset(textOffset);
    editor.getSelectionModel().removeSelection();
    new VariableInplaceRenamer(variable, editor){
      @Override
      protected boolean shouldSelectAll() {
        return true;
      }

      @Override
      protected void moveOffsetAfter(boolean success) {
        super.moveOffsetAfter(success);
        if (success) {
          final PsiNamedElement renamedVariable = getVariable();
          if (renamedVariable != null) {
            editor.getCaretModel().moveToOffset(renamedVariable.getTextRange().getEndOffset());
          }
        }
      }
    }.performInplaceRename();
    return null;
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
            PsiUtil.setModifierProperty(variable, PsiModifier.FINAL, true);
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