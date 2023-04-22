// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithListAccessFix implements IntentionActionWithFixAllOption {
  private final PsiArrayAccessExpression myArrayAccessExpression;

  public ReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression arrayAccessExpression) {
    myArrayAccessExpression = arrayAccessExpression;
  }

  @NotNull
  @Override
  public String getText() {
    return QuickFixBundle.message("replace.with.list.access.text");
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return getText();
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myArrayAccessExpression.isValid()) return false;
    if (!TypeConversionUtil.areTypesAssignmentCompatible(PsiTypes.intType(), myArrayAccessExpression.getIndexExpression())) {
      return false;
    }
    final PsiElement parent = myArrayAccessExpression.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (lExpression.equals(myArrayAccessExpression) && !(parent.getParent() instanceof PsiExpressionStatement)) {
        return false;
      }
    }

    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiType type = arrayExpression.getType();
    final PsiType listType = createUtilListType(project);

    if (type == null || listType == null) return false;


    return listType.isAssignableFrom(type);
  }

  @Nullable
  private PsiType createUtilListType(@NotNull Project project) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_LIST, myArrayAccessExpression.getResolveScope());

    if (listClass == null) return null;

    final PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(listClass);
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiExpression arrayExpression = myArrayAccessExpression.getArrayExpression();
    final PsiExpression indexExpression = myArrayAccessExpression.getIndexExpression();

    if (indexExpression == null) return;

    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiElement parent = myArrayAccessExpression.getParent();
    if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      final PsiExpression rExpression = assignmentExpression.getRExpression();
      if (lExpression.equals(myArrayAccessExpression) && parent.getParent() instanceof PsiExpressionStatement && rExpression != null) {
        replaceWithSet(factory, codeStyleManager, arrayExpression, indexExpression, rExpression, assignmentExpression);
        return;
      }
    }
    replaceWithGet(factory, codeStyleManager, arrayExpression, indexExpression, myArrayAccessExpression);
  }

  private static void replaceWithGet(@NotNull PsiElementFactory factory,
                                     @NotNull CodeStyleManager codeStyleManager,
                                     @NotNull PsiExpression arrayExpression,
                                     @NotNull PsiExpression indexExpression,
                                     @NotNull PsiElement anchor) {

    final PsiElement listAccess = factory.createExpressionFromText(
      arrayExpression.getText() + ".get(" + indexExpression.getText() + ")",
      anchor);
    anchor.replace(codeStyleManager.reformat(listAccess));
  }

  private static void replaceWithSet(@NotNull PsiElementFactory factory,
                                     @NotNull CodeStyleManager codeStyleManager,
                                     @NotNull PsiExpression arrayExpression,
                                     @NotNull PsiExpression indexExpression,
                                     @NotNull PsiExpression expression,
                                     @NotNull PsiElement anchor) {
    final PsiElement listAccess = factory.createExpressionFromText(
      arrayExpression.getText() + ".set(" + indexExpression.getText() + "," + expression.getText() + ")",
      anchor
    );
    anchor.replace(codeStyleManager.reformat(listAccess));
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Nullable
  @Override
  public FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new ReplaceWithListAccessFix(PsiTreeUtil.findSameElementInCopy(myArrayAccessExpression, target));
  }
}
