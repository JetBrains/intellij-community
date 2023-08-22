// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.filters;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;

import java.util.Arrays;


public class PlainTextFilter implements ElementFilter {
  protected final String[] myValue;

  public PlainTextFilter(String... values) {
    myValue = values;
  }

  public PlainTextFilter(String value1, String value2) {
    myValue = new String[]{value1, value2};
  }

  @Override
  public boolean isClassAcceptable(Class hintClass) {
    return true;
  }

  @Override
  public boolean isAcceptable(Object element, PsiElement context) {
    return element != null && Arrays.stream(myValue).anyMatch(v -> v == null || v.equals(getTextByElement(element)));
  }

  protected String getTextByElement(Object element) {
    String elementValue = null;
    if (element instanceof PsiNamedElement) {
      elementValue = ((PsiNamedElement)element).getName();
    }
    else if (element instanceof PsiElement) {
      elementValue = ((PsiElement)element).getText();
    }
    return elementValue;
  }

  @Override
  public String toString() {
    return '(' + StringUtil.join(myValue, " | ") + ')';
  }
}