// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.JavaSuppressionUtil;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class SuppressByJavaCommentFix extends SuppressByCommentModCommandFix {
  public SuppressByJavaCommentFix(@NotNull HighlightDisplayKey key) {
    super(key, PsiStatement.class);
  }

  public SuppressByJavaCommentFix(@NotNull String toolId) {
    super(toolId, PsiStatement.class);
  }

  @Override
  @Nullable
  public PsiElement getContainer(PsiElement context) {
    PsiStatement statement = PsiTreeUtil.getParentOfType(context, PsiStatement.class, false);
    return statement != null && JavaLanguage.INSTANCE.equals(statement.getLanguage()) ? statement : null;
  }

  @Override
  protected void createSuppression(@NotNull Project project,
                                   @NotNull PsiElement element,
                                   @NotNull PsiElement container) throws IncorrectOperationException {
    PsiElement declaredElement = getElementToAnnotate(element, container);
    if (declaredElement == null) {
      super.createSuppression(project, element, container);
    }
    else {
      JavaSuppressionUtil.addSuppressAnnotation(project, container, (PsiVariable)declaredElement, myID);
    }
  }

  @Override
  protected boolean replaceSuppressionComments(@NotNull PsiElement container) {
    if (getElementToAnnotate(container, container) != null) return false;
    return super.replaceSuppressionComments(container);
  }

  @Nullable
  protected PsiElement getElementToAnnotate(@NotNull PsiElement element, @NotNull PsiElement container) {
    return JavaSuppressionUtil.getElementToAnnotate(element, container);
  }

  @Override
  public int getPriority() {
    return 10;
  }
}
