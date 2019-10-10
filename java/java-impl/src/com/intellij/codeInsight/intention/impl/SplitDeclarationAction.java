// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.codeInsight.intention.PsiElementBaseIntentionAction;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.impl.source.tree.Factory;
import com.intellij.psi.impl.source.tree.TreeElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.util.RefactoringUtil;
import com.siyeh.ig.psiutils.CommentTracker;
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
    if (!canModify(element)) return false;
    if (!element.getLanguage().isKindOf(JavaLanguage.INSTANCE)) return false;

    final PsiDeclarationStatement
      context = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class, false, PsiClass.class, PsiCodeBlock.class);
    return context != null && isAvailableOnDeclarationStatement(context);
  }

  private boolean isAvailableOnDeclarationStatement(PsiDeclarationStatement decl) {
    PsiElement[] declaredElements = decl.getDeclaredElements();
    if (declaredElements.length != 1) return false;
    if (!(declaredElements[0] instanceof PsiLocalVariable)) return false;
    PsiLocalVariable var = (PsiLocalVariable)declaredElements[0];
    if (var.getInitializer() == null) return false;
    if (var.getTypeElement().isInferredType() && !PsiTypesUtil.isDenotableType(var.getType(), var)) {
      return false;
    }
    PsiElement parent = decl.getParent();
    if (parent instanceof PsiForStatement) {
      String varName = var.getName();

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

  @Override
  public void invoke(@NotNull Project project, Editor editor, @NotNull PsiElement element) {
    final PsiDeclarationStatement decl = PsiTreeUtil.getParentOfType(element, PsiDeclarationStatement.class);

    if (decl != null) {
      invokeOnDeclarationStatement(decl, PsiManager.getInstance(project), project);
    }
  }

  public static PsiAssignmentExpression invokeOnDeclarationStatement(PsiDeclarationStatement decl, PsiManager psiManager,
                                                                     Project project) {
    if (decl.getDeclaredElements().length == 1) {
      PsiLocalVariable var = (PsiLocalVariable)decl.getDeclaredElements()[0];
      var.normalizeDeclaration();
      final PsiTypeElement typeElement = var.getTypeElement();
      if (typeElement.isInferredType()) {
        PsiTypesUtil.replaceWithExplicitType(typeElement);
      }
      final String name = var.getName();
      PsiExpressionStatement statement = (PsiExpressionStatement)JavaPsiFacade.getElementFactory(psiManager.getProject())
                                                                              .createStatementFromText(name + "=xxx;", decl);
      statement = (PsiExpressionStatement)CodeStyleManager.getInstance(project).reformat(statement);
      PsiAssignmentExpression assignment = (PsiAssignmentExpression)statement.getExpression();
      PsiExpression initializer = var.getInitializer();
      assert initializer != null;
      PsiExpression rExpression = RefactoringUtil.convertInitializerToNormalExpression(initializer, var.getType());

      final PsiExpression expression = assignment.getRExpression();
      assert expression != null;
      expression.replace(rExpression);

      PsiElement block = decl.getParent();
      if (block instanceof PsiForStatement) {
        final PsiDeclarationStatement varDeclStatement =
          JavaPsiFacade.getElementFactory(psiManager.getProject()).createVariableDeclarationStatement(name, var.getType(), null);

        // For index can't be final, right?
        for (PsiElement varDecl : varDeclStatement.getDeclaredElements()) {
          if (varDecl instanceof PsiModifierListOwner) {
            final PsiModifierList modList = ((PsiModifierListOwner)varDecl).getModifierList();
            assert modList != null;
            modList.setModifierProperty(PsiModifier.FINAL, false);
          }
        }

        final PsiElement parent = block.getParent();
        PsiExpressionStatement replaced = (PsiExpressionStatement)new CommentTracker().replaceAndRestoreComments(decl, statement);
        if (!(parent instanceof PsiCodeBlock)) {
          final PsiBlockStatement blockStatement =
            (PsiBlockStatement)JavaPsiFacade.getElementFactory(project).createStatementFromText("{}", block);
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
        try {
          PsiElement declaredElement = decl.getDeclaredElements()[0];
          if (!PsiUtil.isJavaToken(declaredElement.getLastChild(), JavaTokenType.SEMICOLON)) {
            TreeElement semicolon = Factory.createSingleLeafElement(JavaTokenType.SEMICOLON, ";", 0, 1, null, decl.getManager());
            CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(decl.addAfter(semicolon.getPsi(), declaredElement));
          }
          return (PsiAssignmentExpression)((PsiExpressionStatement)block.addAfter(statement, decl)).getExpression();
        }
        finally {
          initializer.delete();
        }
      }
    }
    else {
      ((PsiLocalVariable)decl.getDeclaredElements()[0]).normalizeDeclaration();
    }
    return null;
  }
}
