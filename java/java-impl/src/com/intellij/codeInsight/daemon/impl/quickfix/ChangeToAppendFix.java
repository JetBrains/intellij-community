// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.util.ChangeToAppendUtil;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.psi.*;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.util.InheritanceUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class ChangeToAppendFix extends PsiUpdateModCommandAction<PsiAssignmentExpression> {
  private final IElementType myTokenType;
  private final PsiType myLhsType;
  private volatile TypeInfo myTypeInfo;

  public ChangeToAppendFix(@NotNull IElementType eqOpSign, @NotNull PsiType lType, @NotNull PsiAssignmentExpression assignmentExpression) {
    super(assignmentExpression);
    myTokenType = eqOpSign;
    myLhsType = lType;
  }

  @Override
  protected @Nullable Presentation getPresentation(@NotNull ActionContext context, @NotNull PsiAssignmentExpression assignmentExpression) {
    if (JavaTokenType.PLUSEQ != myTokenType || !getTypeInfo().appendable) return null;
    String text = QuickFixBundle.message("change.to.append.text", ChangeToAppendUtil.buildAppendExpression(
      assignmentExpression.getRExpression(), getTypeInfo().useStringValueOf,
      new StringBuilder(assignmentExpression.getLExpression().getText())));
    return Presentation.of(text);
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return QuickFixBundle.message("change.to.append.family");
  }

  @Override
  protected void invoke(@NotNull ActionContext context, @NotNull PsiAssignmentExpression assignmentExpression, @NotNull ModPsiUpdater updater) {
    final PsiExpression appendExpression =
      ChangeToAppendUtil.buildAppendExpression(assignmentExpression.getLExpression(), assignmentExpression.getRExpression());
    if (appendExpression == null) return;
    assignmentExpression.replace(appendExpression);
  }

  @NotNull
  private TypeInfo getTypeInfo() {
    if (myTypeInfo != null) return myTypeInfo;
    myTypeInfo = calculateTypeInfo();
    return myTypeInfo;
  }

  @NotNull
  private TypeInfo calculateTypeInfo() {
    if (myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUILDER) ||
        myLhsType.equalsToText(CommonClassNames.JAVA_LANG_STRING_BUFFER)) {
      return new TypeInfo(true, false);
    }
    if (InheritanceUtil.isInheritor(myLhsType, "java.lang.Appendable")) {
      return new TypeInfo(true, true);
    }
    return new TypeInfo(false, false);
  }

  private record TypeInfo(boolean appendable, boolean useStringValueOf) {
  }
}
