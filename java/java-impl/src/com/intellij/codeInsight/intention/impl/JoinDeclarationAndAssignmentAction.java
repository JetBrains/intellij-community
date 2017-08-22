/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.editorActions.DeclarationJoinLinesHandler;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.codeInspection.RemoveInitializerFix;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class JoinDeclarationAndAssignmentAction extends PsiElementBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.join.declaration.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {

    if (element instanceof PsiCompiledElement) return false;
    if (!element.getManager().isInProject(element)) return false;
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;

    if (getPair(element) != null) {
      setText(CodeInsightBundle.message("intention.join.declaration.text"));
      return true;
    }
    return false;
  }

  private static Pair<PsiLocalVariable, PsiAssignmentExpression> getPair(PsiElement element) {
    PsiAssignmentExpression assignmentExpression = PsiTreeUtil.getParentOfType(element, PsiAssignmentExpression.class);
    PsiDeclarationStatement declarationStatement = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);
    if (declarationStatement != null) {
      assignmentExpression = getAssignmentStatement(declarationStatement);
    } else if (assignmentExpression != null) {
      declarationStatement = getDeclarationStatement(assignmentExpression);
    }

    if (declarationStatement != null && assignmentExpression != null) {
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      final PsiExpression rExpression = assignmentExpression.getRExpression();
      if (lExpression instanceof PsiReferenceExpression && rExpression != null) {
        final PsiElement resolve = ((PsiReferenceExpression)lExpression).resolve();
        if (resolve instanceof PsiLocalVariable && resolve.getParent() == declarationStatement) {
          final PsiLocalVariable variable = (PsiLocalVariable)resolve;
          if (ReferencesSearch.search(variable, new LocalSearchScope(rExpression), false).findFirst() != null) {
            return null;
          }
          return Pair.createNonNull(variable, assignmentExpression);
        }
      }
    }
    return null;
  }

  private static PsiAssignmentExpression getAssignmentStatement(PsiDeclarationStatement statement) {
    final PsiElement element = PsiTreeUtil.skipWhitespacesForward(statement);
    if (element instanceof PsiExpressionStatement) {
      final PsiExpression expression = ((PsiExpressionStatement)element).getExpression();
      if (expression instanceof PsiAssignmentExpression) {
        return (PsiAssignmentExpression)expression;
      }
    }
    return null;
  }

  private static PsiDeclarationStatement getDeclarationStatement(PsiAssignmentExpression assignmentExpression) {
    final PsiElement parent = assignmentExpression.getParent();
    if (parent instanceof PsiExpressionStatement) {
      final PsiElement element = PsiTreeUtil.skipWhitespacesBackward(parent);
      if (element instanceof PsiDeclarationStatement) {
        return (PsiDeclarationStatement)element;
      }
    }
    return null;
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    if (!FileModificationService.getInstance().preparePsiElementForWrite(element)) return;

    final Pair<PsiLocalVariable, PsiAssignmentExpression> pair = getPair(element);
    if (pair == null) return;
    final PsiLocalVariable variable = pair.getFirst();
    final PsiAssignmentExpression assignmentExpression = pair.getSecond();
    final PsiExpression initializer = variable.getInitializer();
    if (initializer != null && assignmentExpression.getOperationTokenType() == JavaTokenType.EQ) {
      RemoveInitializerFix.sideEffectAwareRemove(project, initializer, initializer, variable);
    }
    WriteAction.run(() -> {
      final PsiExpression initializerExpression = DeclarationJoinLinesHandler.getInitializerExpression(variable, assignmentExpression);
      variable.setInitializer(initializerExpression);
      assignmentExpression.delete();
    });
  }
}
