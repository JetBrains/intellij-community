// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.PsiEquivalenceUtil;
import com.intellij.codeInsight.daemon.impl.actions.IntentionActionWithFixAllOption;
import com.intellij.codeInspection.LocalQuickFixAndIntentionActionOnPsiElement;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiMethodCallExpression;
import com.siyeh.ig.psiutils.MethodCallUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class RemoveRepeatingCallFix extends LocalQuickFixAndIntentionActionOnPsiElement implements IntentionActionWithFixAllOption {
  private final String myMethodName;

  private RemoveRepeatingCallFix(PsiMethodCallExpression call, String methodName) {
    super(call);
    myMethodName = methodName;
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiMethodCallExpression call = (PsiMethodCallExpression)startElement;
    PsiExpression qualifier = call.getMethodExpression().getQualifierExpression();
    if (qualifier == null) {
      return;
    }
    call.replace(qualifier);
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("intention.name.remove.repeating.call", myMethodName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaAnalysisBundle.message("intention.family.name.remove.repeating.call");
  }

  public static @Nullable RemoveRepeatingCallFix createFix(@NotNull PsiMethodCallExpression call) {
    String name = call.getMethodExpression().getReferenceName();
    if (name == null) return null;
    PsiMethodCallExpression qualifierCall = MethodCallUtils.getQualifierMethodCall(call);
    if (qualifierCall == null) return null;
    String qualifierName = qualifierCall.getMethodExpression().getReferenceName();
    if (!name.equals(qualifierName)) return null;
    if (!PsiEquivalenceUtil.areElementsEquivalent(call.getArgumentList(), qualifierCall.getArgumentList())) return null;
    return new RemoveRepeatingCallFix(call, name);
  }
}
