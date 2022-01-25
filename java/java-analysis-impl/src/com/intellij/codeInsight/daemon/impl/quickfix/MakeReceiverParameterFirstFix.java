// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.siyeh.ig.psiutils.CommentTracker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MakeReceiverParameterFirstFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  public MakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter) {
    super(parameter);
  }

  @Override
  public @IntentionName @NotNull String getText() {
    return QuickFixBundle.message("make.receiver.parameter.first.text");
  }

  @Override
  public @NotNull @IntentionFamilyName String getFamilyName() {
    return QuickFixBundle.message("make.receiver.parameter.first.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project,
                             @NotNull PsiFile file,
                             @Nullable Editor editor,
                             @NotNull PsiElement startElement,
                             @NotNull PsiElement endElement) {
    if (!startElement.isValid() || !BaseIntentionAction.canModify(startElement)) return false;
    final PsiParameterList parameterList = ObjectUtils.tryCast(startElement.getParent(), PsiParameterList.class);
    return parameterList != null && PsiUtil.isJavaToken(parameterList.getFirstChild(), JavaTokenType.LPARENTH);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiParameterList parameterList = ObjectUtils.tryCast(startElement.getParent(), PsiParameterList.class);
    if (parameterList == null) return;
    final PsiElement firstChild = parameterList.getFirstChild();
    if (!PsiUtil.isJavaToken(firstChild, JavaTokenType.LPARENTH)) return;
    final PsiElement movedParameter = parameterList.addAfter(startElement, firstChild);
    moveComments(startElement, movedParameter, parameterList, true);
    moveComments(startElement, movedParameter, parameterList, false);
    startElement.delete();
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
