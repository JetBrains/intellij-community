// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.BaseIntentionAction;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiParameterList;
import com.intellij.psi.PsiReceiverParameter;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

public class MakeReceiverParameterFirstFix implements IntentionAction {
  @NotNull final PsiReceiverParameter myParameter;

  public MakeReceiverParameterFirstFix(@NotNull PsiReceiverParameter parameter) {
    myParameter = parameter;
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
                             Editor editor,
                             PsiFile file) {
    if (!myParameter.isValid() || !BaseIntentionAction.canModify(myParameter)) return false;
    final PsiParameterList parameterList = ObjectUtils.tryCast(myParameter.getParent(), PsiParameterList.class);
    return parameterList != null && !parameterList.isEmpty();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    final PsiParameterList parameterList = ObjectUtils.tryCast(myParameter.getParent(), PsiParameterList.class);
    if (parameterList == null || parameterList.isEmpty()) return;
    parameterList.addBefore(myParameter, parameterList.getParameter(0));
    myParameter.delete();
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
