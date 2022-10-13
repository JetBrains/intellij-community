// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.lookup;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.ui.IconManager;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Set;

public class ExpressionLookupItem extends LookupElement implements TypedLookupItem {
  private final PsiExpression myExpression;
  private final Icon myIcon;
  private final String myPresentableText;
  private final String myLookupString;
  private final Set<String> myAllLookupStrings;

  public ExpressionLookupItem(final PsiExpression expression) {
    this(expression, getExpressionIcon(expression), expression.getText(), expression.getText());
  }

  public ExpressionLookupItem(final PsiExpression expression, @Nullable Icon icon, String presentableText, String... lookupStrings) {
    myExpression = expression;
    myPresentableText = presentableText;
    myIcon = icon;
    myLookupString = lookupStrings[0];
    myAllLookupStrings = ContainerUtil.immutableSet(lookupStrings);
  }

  @Nullable
  private static Icon getExpressionIcon(@NotNull PsiExpression expression) {
    if (expression instanceof PsiReferenceExpression) {
      final PsiElement element = ((PsiReferenceExpression)expression).resolve();
      if (element != null) {
        return element.getIcon(0);
      }
    }
    if (expression instanceof PsiMethodCallExpression) {
      return IconManager.getInstance().getPlatformIcon(com.intellij.ui.PlatformIcons.Method);
    }
    return null;
  }

  @NotNull
  @Override
  public PsiExpression getObject() {
    return myExpression;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setIcon(myIcon);
    presentation.setItemText(myPresentableText);
    PsiType type = getType();
    presentation.setTypeText(type == null ? null : type.getPresentableText());
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    context.commitDocument();
    JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(context.getFile(), context.getStartOffset(), context.getTailOffset());
  }

  @Override
  public PsiType getType() {
    return myExpression.getType();
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof ExpressionLookupItem && myLookupString.equals(((ExpressionLookupItem)o).myLookupString);
  }

  @Override
  public int hashCode() {
    return myLookupString.hashCode();
  }

  @NotNull
  @Override
  public String getLookupString() {
    return myLookupString;
  }

  @Override
  public Set<String> getAllLookupStrings() {
    return myAllLookupStrings;
  }
}