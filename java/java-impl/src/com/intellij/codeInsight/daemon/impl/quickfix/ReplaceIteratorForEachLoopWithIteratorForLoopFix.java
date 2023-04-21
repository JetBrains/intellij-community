// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.application.options.CodeStyle;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import com.siyeh.ig.psiutils.VariableNameGenerator;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class ReplaceIteratorForEachLoopWithIteratorForLoopFix implements IntentionAction {
  private final PsiForeachStatement myStatement;

  public ReplaceIteratorForEachLoopWithIteratorForLoopFix(@NotNull PsiForeachStatement statement) {
    myStatement = statement;
  }

  @Override
  public @NotNull FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ReplaceIteratorForEachLoopWithIteratorForLoopFix(PsiTreeUtil.findSameElementInCopy(myStatement, target));
  }

  @Nls
  @NotNull
  @Override
  public String getText() {
    return getFamilyName();
  }

  @Nls
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("replace.for.each.loop.with.iterator.for.loop");
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    return myStatement.isValid() && BaseIntentionAction.canModify(myStatement);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiExpression iteratedValue = myStatement.getIteratedValue();
    if (iteratedValue == null) {
      return;
    }
    final PsiType iteratedValueType = iteratedValue.getType();
    if (iteratedValueType == null) {
      return;
    }
    final PsiParameter iterationParameter = myStatement.getIterationParameter();
    final String iterationParameterName = iterationParameter.getName();
    final PsiStatement forEachBody = myStatement.getBody();

    final PsiElementFactory elementFactory = JavaPsiFacade.getElementFactory(project);
    final JavaCodeStyleManager javaStyleManager = JavaCodeStyleManager.getInstance(project);
    final String name = new VariableNameGenerator(myStatement, VariableKind.LOCAL_VARIABLE)
      .byName("it", "iter", "iterator").generate(true);
    PsiForStatement newForLoop = (PsiForStatement)elementFactory.createStatementFromText(
      "for (Iterator " + name + " = initializer; " + name + ".hasNext();) { var next = " + name + ".next(); }", myStatement);

    final PsiDeclarationStatement newDeclaration = (PsiDeclarationStatement)newForLoop.getInitialization();
    if (newDeclaration == null) return;
    final PsiLocalVariable newIteratorVariable = (PsiLocalVariable)newDeclaration.getDeclaredElements()[0];
    final PsiTypeElement newIteratorTypeElement = elementFactory.createTypeElement(iteratedValueType);
    newIteratorVariable.getTypeElement().replace(newIteratorTypeElement);
    newIteratorVariable.setInitializer(iteratedValue);

    final PsiBlockStatement newBody = (PsiBlockStatement)newForLoop.getBody();
    if (newBody == null) return;
    final PsiCodeBlock newBodyBlock = newBody.getCodeBlock();

    final PsiDeclarationStatement newFirstStatement = (PsiDeclarationStatement)newBodyBlock.getStatements()[0];
    final PsiLocalVariable newItemVariable = (PsiLocalVariable)newFirstStatement.getDeclaredElements()[0];
    PsiTypeElement typeElement = iterationParameter.getTypeElement();
    if (typeElement == null || !typeElement.isInferredType()) {
      final PsiTypeElement newItemTypeElement = elementFactory.createTypeElement(iterationParameter.getType());
      newItemVariable.getTypeElement().replace(newItemTypeElement);
    }
    newItemVariable.setName(iterationParameterName);
    final CodeStyleSettings codeStyleSettings = CodeStyle.getSettings(file);
    if (codeStyleSettings.getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_LOCALS) {
      final PsiModifierList modifierList = newItemVariable.getModifierList();
      if (modifierList != null) modifierList.setModifierProperty(PsiModifier.FINAL, true);
    }
    final CodeStyleManager styleManager = CodeStyleManager.getInstance(project);
    newForLoop = (PsiForStatement)javaStyleManager.shortenClassReferences(newForLoop);
    newForLoop = (PsiForStatement)styleManager.reformat(newForLoop);

    if (forEachBody instanceof PsiBlockStatement) {
      final PsiCodeBlock bodyCodeBlock = ((PsiBlockStatement)forEachBody).getCodeBlock();
      final PsiElement firstBodyElement = bodyCodeBlock.getFirstBodyElement();
      final PsiElement lastBodyElement = bodyCodeBlock.getLastBodyElement();
      if (firstBodyElement != null && lastBodyElement != null) {
        newBodyBlock.addRangeAfter(firstBodyElement, lastBodyElement, newFirstStatement);
      }
    }
    else if (forEachBody != null && !(forEachBody instanceof PsiEmptyStatement)) {
      newBodyBlock.addAfter(forEachBody, newFirstStatement);
    }

    myStatement.replace(newForLoop);
  }
}
