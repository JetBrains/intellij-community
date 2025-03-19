// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeReceiverParameterFirstFix extends PsiUpdateModCommandAction<PsiReceiverParameter> {
  public MakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter) {
    super(parameter);
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return QuickFixBundle.message("make.receiver.parameter.first.family");
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiReceiverParameter parameter) {
    final PsiParameterList parameterList = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parameterList == null || !PsiUtil.isJavaToken(parameterList.getFirstChild(), JavaTokenType.LPARENTH)) return null;
    return Presentation.of(QuickFixBundle.message("make.receiver.parameter.first.text"));
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiReceiverParameter parameter, @NotNull ModPsiUpdater updater) {
    final PsiParameterList parameterList = ObjectUtils.tryCast(parameter.getParent(), PsiParameterList.class);
    if (parameterList == null) return;
    final PsiElement firstChild = parameterList.getFirstChild();
    if (!PsiUtil.isJavaToken(firstChild, JavaTokenType.LPARENTH)) return;
    final PsiElement movedParameter = parameterList.addAfter(parameter, firstChild);
    moveComments(parameter, movedParameter, parameterList, true);
    moveComments(parameter, movedParameter, parameterList, false);
    parameter.delete();
    PsiElement next = firstChild.getNextSibling();
    if (next instanceof PsiWhiteSpace) {
      next.delete();
    }
  }

  private static void moveComments(@NotNull PsiElement oldParameter,
                                   @NotNull PsiElement movedParameter,
                                   @NotNull PsiParameterList parameterList,
                                   boolean beforeParameter) {
    final PsiElement neighbour = beforeParameter
                                 ? PsiTreeUtil.skipWhitespacesAndCommentsBackward(oldParameter)
                                 : PsiTreeUtil.skipWhitespacesAndCommentsForward(oldParameter);
    if (neighbour == null) return;
    if (beforeParameter) {
      if (!PsiUtil.isJavaToken(neighbour, JavaTokenType.COMMA)) return;
    } else {
      if (!PsiUtil.isJavaToken(neighbour, JavaTokenType.RPARENTH) && !PsiUtil.isJavaToken(neighbour, JavaTokenType.COMMA)) return;
    }
    final String comments = beforeParameter
                            ? CommentTracker.commentsBetween(neighbour, oldParameter)
                            : CommentTracker.commentsBetween(oldParameter, neighbour);
    final PsiElementFactory factory = JavaPsiFacade.getElementFactory(oldParameter.getProject());
    final PsiCodeBlock block = factory.createCodeBlockFromText("{" + comments + "}", parameterList);
    for (PsiElement child : beforeParameter ? block.getChildren() : ArrayUtil.reverseArray(block.getChildren())) {
      if (child instanceof PsiComment || child instanceof PsiWhiteSpace) {
        if (beforeParameter) {
          parameterList.addBefore(child, movedParameter);
        } else {
          parameterList.addAfter(child, movedParameter);
        }
      }
    }
  }
}
