/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.codeInsight.lookup;

import com.intellij.psi.*;
import com.intellij.util.PlatformIcons;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.Set;

/**
 * @author peter
*/
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
    myAllLookupStrings = Collections.unmodifiableSet(ContainerUtil.newHashSet(lookupStrings));
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
      return PlatformIcons.METHOD_ICON;
    }
    return null;
  }

  @NotNull
  @Override
  public PsiExpression getObject() {
    return myExpression;
  }

  @Override
  public void renderElement(LookupElementPresentation presentation) {
    presentation.setIcon(myIcon);
    presentation.setItemText(myPresentableText);
    PsiType type = getType();
    presentation.setTypeText(type == null ? null : type.getPresentableText());
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