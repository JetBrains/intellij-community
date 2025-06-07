// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SuppressParameterFix extends AbstractBatchSuppressByNoInspectionCommentModCommandFix {
  private String myAlternativeID;

  public SuppressParameterFix(@NotNull HighlightDisplayKey key) {
    this(key.getID());
    myAlternativeID = HighlightDisplayKey.getAlternativeID(key);
  }

  public SuppressParameterFix(String ID) {
    super(ID, false);
  }

  @Override
  public @NotNull String getText() {
    return JavaAnalysisBundle.message("suppress.for.parameter");
  }

  @Override
  public @Nullable PsiElement getContainer(PsiElement context) {
    PsiParameter psiParameter = PsiTreeUtil.getParentOfType(context, PsiParameter.class, false);
    return psiParameter != null && psiParameter.getTypeElement() != null && JavaSuppressionUtil.canHave15Suppressions(psiParameter) ? psiParameter : null;
  }

  @Override
  protected boolean replaceSuppressionComments(@NotNull PsiElement container) {
    return false;
  }

  @Override
  protected void createSuppression(@NotNull Project project, @NotNull PsiElement element, @NotNull PsiElement cont)
    throws IncorrectOperationException {
    PsiModifierListOwner container = (PsiModifierListOwner)cont;
    final PsiModifierList modifierList = container.getModifierList();
    if (modifierList != null) {
      final String id = SuppressFix.getID(container, myAlternativeID);
      JavaSuppressionUtil.addSuppressAnnotation(project, container, container, id != null ? id : myID);
    }
  }

  @Override
  public int getPriority() {
    return 30;
  }
}
