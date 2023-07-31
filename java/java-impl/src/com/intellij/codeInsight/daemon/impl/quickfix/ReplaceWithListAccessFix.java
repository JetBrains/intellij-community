// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.TypeConversionUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ReplaceWithListAccessFix extends PsiUpdateModCommandAction<PsiArrayAccessExpression> {
  public ReplaceWithListAccessFix(@NotNull PsiArrayAccessExpression arrayAccessExpression) {
    super(arrayAccessExpression);
  }
  
  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("replace.with.list.access.text");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiArrayAccessExpression arrayAccess) {
    if (!TypeConversionUtil.areTypesAssignmentCompatible(PsiTypes.intType(), arrayAccess.getIndexExpression())) {
      return null;
    }
    final PsiElement parent = arrayAccess.getParent();
    if (parent instanceof PsiAssignmentExpression) {
      final PsiExpression lExpression = ((PsiAssignmentExpression)parent).getLExpression();
      if (lExpression.equals(arrayAccess) && !(parent.getParent() instanceof PsiExpressionStatement)) {
        return null;
      }
    }

    final PsiExpression arrayExpression = arrayAccess.getArrayExpression();
    final PsiType type = arrayExpression.getType();
    final PsiType listType = createUtilListType(context.project(), arrayAccess);

    if (type == null || listType == null || !listType.isAssignableFrom(type)) return null;
    return Presentation.of(getFamilyName()).withFixAllOption(this);
  }

  @Nullable
  private static PsiType createUtilListType(@NotNull Project project, @NotNull PsiArrayAccessExpression arrayAccess) {
    final JavaPsiFacade facade = JavaPsiFacade.getInstance(project);
    final PsiClass listClass = JavaPsiFacade.getInstance(project).findClass(CommonClassNames.JAVA_UTIL_LIST, arrayAccess.getResolveScope());

    if (listClass == null) return null;

    final PsiElementFactory factory = facade.getElementFactory();
    return factory.createType(listClass);
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiArrayAccessExpression arrayAccess, @NotNull ModPsiUpdater updater) {
    final PsiExpression arrayExpression = arrayAccess.getArrayExpression();
    final PsiExpression indexExpression = arrayAccess.getIndexExpression();

    if (indexExpression == null) return;

    Project project = context.project();
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(project);
    final CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);

    final PsiElement parent = arrayAccess.getParent();
    if (parent instanceof PsiAssignmentExpression assignmentExpression) {
      final PsiExpression lExpression = assignmentExpression.getLExpression();
      final PsiExpression rExpression = assignmentExpression.getRExpression();
      if (lExpression.equals(arrayAccess) && parent.getParent() instanceof PsiExpressionStatement && rExpression != null) {
        replaceWithSet(factory, codeStyleManager, arrayExpression, indexExpression, rExpression, assignmentExpression);
        return;
      }
    }
    replaceWithGet(factory, codeStyleManager, arrayExpression, indexExpression, arrayAccess);
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
}
