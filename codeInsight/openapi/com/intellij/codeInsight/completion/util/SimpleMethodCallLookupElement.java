/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiFormatUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class SimpleMethodCallLookupElement extends LookupElement {
  private static final LookupElementRenderer<SimpleMethodCallLookupElement> METHOD_LOOKUP_RENDERER = new LookupElementRenderer<SimpleMethodCallLookupElement>() {
    public void renderElement(final SimpleMethodCallLookupElement element, final LookupElementPresentation presentation) {
      final PsiMethod method = element.getMethod();
      presentation.setIcon(method.getIcon(Iconable.ICON_FLAG_VISIBILITY));
      presentation.setItemText(method.getName());
      presentation.setTailText(PsiFormatUtil.formatMethod(method,
                                          PsiSubstitutor.EMPTY,
                                          PsiFormatUtil.SHOW_PARAMETERS,
                                          PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE));
      final PsiType returnType = method.getReturnType();
      if (returnType != null) {
        presentation.setTypeText(returnType.getCanonicalText());
      }
    }
  };
  private final PsiMethod myMethod;

  public SimpleMethodCallLookupElement(final PsiMethod method) {
    myMethod = method;
  }

  @NotNull
  public String getLookupString() {
    return myMethod.getName();
  }

  public InsertHandler<? extends LookupElement> getInsertHandler() {
    return new MethodParenthesesHandler(myMethod, true);
  }

  public PsiMethod getMethod() {
    return myMethod;
  }

  @NotNull
  protected LookupElementRenderer<? extends SimpleMethodCallLookupElement> getRenderer() {
    return METHOD_LOOKUP_RENDERER;
  }
}
