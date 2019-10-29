// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.actions;

import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ven
 */
public class SuppressByCommentFix extends AbstractBatchSuppressByNoInspectionCommentFix {
  protected Class<? extends PsiElement> mySuppressionHolderClass;

  public SuppressByCommentFix(@NotNull HighlightDisplayKey key, @NotNull Class<? extends PsiElement> suppressionHolderClass) {
    this(key.getID(), suppressionHolderClass);
  }

  public SuppressByCommentFix(@NotNull String toolId, @NotNull Class<? extends PsiElement> suppressionHolderClass) {
    this(toolId);
    mySuppressionHolderClass = suppressionHolderClass;
  }

  private SuppressByCommentFix(final String ID) {
    super(ID, false);
  }

  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  @NotNull
  public String getText() {
    return InspectionsBundle.message("suppress.inspection.statement");
  }

  @Override
  @Nullable
  public PsiElement getContainer(PsiElement context) {
    return PsiTreeUtil.getParentOfType(context, mySuppressionHolderClass);
  }
}
