// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public abstract class MethodArgumentFix implements IntentionAction {
  private static final Logger LOG = Logger.getInstance(MethodArgumentFix.class);

  protected final SmartPsiElementPointer<PsiExpressionList> myArgList;
  protected final int myIndex;
  protected final ArgumentFixerActionFactory myArgumentFixerActionFactory;
  @NotNull
  protected final PsiType myToType;

  protected MethodArgumentFix(@NotNull PsiExpressionList list, int i, @NotNull PsiType toType, @NotNull ArgumentFixerActionFactory fixerActionFactory) {
    myArgList = SmartPointerManager.createPointer(list);
    myIndex = i;
    myArgumentFixerActionFactory = fixerActionFactory;
    myToType = toType instanceof PsiEllipsisType ? ((PsiEllipsisType) toType).toArrayType() : toType;
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    PsiExpressionList list = myArgList.getElement();
    if (list != null && myToType.isValid() && PsiTypesUtil.allTypeParametersResolved(list, myToType)) {
      PsiExpression[] args = list.getExpressions();
      return args.length > myIndex && args[myIndex] != null && args[myIndex].isValid();
    }
    return false;
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) {
    PsiExpressionList list = myArgList.getElement();
    if (list == null) return;
    PsiExpression expression = list.getExpressions()[myIndex];

    LOG.assertTrue(expression != null && expression.isValid());
    PsiExpression modified = myArgumentFixerActionFactory.getModifiedArgument(expression, myToType);
    LOG.assertTrue(modified != null, myArgumentFixerActionFactory);
    PsiElement newElement = expression.replace(modified);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(newElement);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("fix.argument.family");
  }
}
