// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import org.jetbrains.annotations.NotNull;

public class SimpleMethodCallLookupElement extends LookupElement {
  private final PsiMethod myMethod;

  public SimpleMethodCallLookupElement(final PsiMethod method) {
    myMethod = method;
  }

  @Override
  public @NotNull String getLookupString() {
    return myMethod.getName();
  }

  @Override
  public void handleInsert(@NotNull InsertionContext context) {
    new MethodParenthesesHandler(myMethod, true).handleInsert(context, this);
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  @Override
  public void renderElement(@NotNull LookupElementPresentation presentation) {
    presentation.setIcon(myMethod.getIcon(Iconable.ICON_FLAG_VISIBILITY));
    presentation.setItemText(myMethod.getName());
    presentation.setTailText(PsiFormatUtil.formatMethod(myMethod,
                                                        PsiSubstitutor.EMPTY,
                                                        PsiFormatUtilBase.SHOW_PARAMETERS,
                                                        PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE));
    final PsiType returnType = myMethod.getReturnType();
    if (returnType != null) {
      presentation.setTypeText(returnType.getCanonicalText());
    }
  }

}
