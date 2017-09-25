/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author max
 */
public class SplitDeclarationAction extends PsiElementBaseIntentionAction {
  @Override
  @NotNull
  public String getFamilyName() {
    return CodeInsightBundle.message("intention.split.declaration.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, @NotNull PsiElement element) {

    if (element instanceof PsiCompiledElement) return false;
    if (!element.getManager().isInProject(element)) return false;
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;

    final PsiElement context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, PsiClass.class);
    if (context instanceof PsiDeclarationStatement) {
      return isAvailableOnDeclarationStatement((PsiDeclarationStatement)context, element);
    }

    PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
    if (field != null && PsiTreeUtil.getParentOfType(element, PsiDocComment.class) == null && isAvailableOnField(field)) {
      setText(CodeInsightBundle.message("intention.split.declaration.text"));
      return true;
    }
    return false;
  }

  private static boolean isAvailableOnField(PsiField field) {
    final PsiTypeElement typeElement = field.getTypeElement();
    if (typeElement == null) return false;
    if (PsiTreeUtil.getParentOfType(typeElement, PsiField.class) != field) return true;

    PsiElement nextField = field.getNextSibling();
    while (nextField != null && !(nextField instanceof PsiField)) nextField = nextField.getNextSibling();

    if (nextField != null && ((PsiField)nextField).getTypeElement() == typeElement) return true;

    return false;
  }

  private boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement decl, PsiElement element) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length == 0) return false;
    if (!(declaredElements[0] instanceof PsiLocalVariable)) return false;
    if (declaredElements.length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)declaredElements[0];
      if (var.getInitializer() == null) return false;
      if (var.getTypeElement().isInferredType()) {
        return false;
      } 
      PsiElement parent = decl.getParent();
      if (parent instanceof PsiForStatement) {
        String varName = var.getName();
        if (varName == null) {
          return false;
        }

        parent = parent.getNextSibling();
        while (parent != null) {
          Ref<Boolean> conflictFound = new Ref<>(false);
          parent.accept(new JavaRecursiveElementWalkingVisitor() {
            @Override
            public void visitClass(PsiClass aClass) { }

            @Override
            public void visitVariable(PsiVariable variable) {
              super.visitVariable(variable);
              if (varName.equals(variable.getName())) {
                conflictFound.set(true);
                stopWalking();
              }
            }
          });
          if (conflictFound.get()) {
            return false;
          }
          parent = parent.getNextSibling();
        }
      }
      setText(CodeInsightBundle.message("intention.split.declaration.assignment.text"));
      return true;
    }
    else {
      if (decl.getParent() instanceof PsiForStatement) return false;

      setText(CodeInsightBundle.message("intention.split.declaration.text"));
      return true;
    }
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) throws IncorrectOperationException {
    final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);

    final PsiManager psiManager = PsiManager.getInstance(project);
    if (decl != null) {
      invokeOnDeclarationStatement(decl, psiManager, project);
    }
    else {
      PsiField field = PsiTreeUtil.getParentOfType(element, PsiField.class);
      if (field != null) {
        field.normalizeDeclaration();
      }
    }
  }

  public static PsiAssignmentExpression invokeOnDeclarationStatement(PsiDeclarationStatement decl, PsiManager psiManager,
                                                                     Project project) throws IncorrectOperationException {
    if (decl.getDeclaredElements().length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)decl.getDeclaredElements()[0];
      var.normalizeDeclaration();
      PsiExpressionStatement statement = (PsiExpressionStatement)JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory()
        .createStatementFromText(var.getName() + "=xxx;", null);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(statement);
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
      PsiExpression initializer = var.getInitializer();
      PsiExpression rExpression = RefactoringUtil.convertInitializerToNormalExpression(initializer, var.getType());

      assignment.getRExpression().replace(rExpression);
      initializer.delete();

      PsiElement block = decl.getParent();
      if (block instanceof PsiForStatement) {
        final PsiDeclarationStatement varDeclStatement =
          JavaPsiFacade.getInstance(psiManager.getProject()).getElementFactory().createVariableDeclarationStatement(var.getName(), var.getType(), null);

        // For index can't be final, right?
        for (PsiElement varDecl : varDeclStatement.getDeclaredElements()) {
          if (varDecl instanceof PsiModifierListOwner) {
            final PsiModifierList modList = ((PsiModifierListOwner)varDecl).getModifierList();
            assert modList != null;
            modList.setModifierProperty(PsiModifier.FINAL, false);
          }
        }

        final PsiElement parent = block.getParent();
        PsiExpressionStatement replaced = (PsiExpressionStatement)decl.replace(statement);
        if (!(parent instanceof PsiCodeBlock)) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("{}", null);
          final PsiCodeBlock codeBlock = blockStatement.getCodeBlock();
          codeBlock.add(varDeclStatement);
          codeBlock.add(block);
          block.replace(blockStatement);
        }
        else {
          parent.addBefore(varDeclStatement, block);
        }
        return (PsiAssignmentExpression)replaced.getExpression();
      }
      else {
        return (PsiAssignmentExpression)((PsiExpressionStatement)block.addAfter(statement, decl)).getExpression();
      }
    }
    else {
      ((PsiLocalVariable)decl.getDeclaredElements()[0]).normalizeDeclaration();
    }
    return null;
  }
}
