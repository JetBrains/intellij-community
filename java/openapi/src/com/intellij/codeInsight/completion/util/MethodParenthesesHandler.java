/*
 * Copyright (c) 2000-2005 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.codeInsight.completion.util;

import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.codeStyle.CodeStyleSettingsManager;

/**
 * @author peter
 */
public class MethodParenthesesHandler extends ParenthesesInsertHandler<LookupElement> {
  private final PsiMethod myMethod;
  private final boolean myOverloadsMatter;

  public MethodParenthesesHandler(final PsiMethod method, boolean overloadsMatter) {
    myMethod = method;
    myOverloadsMatter = overloadsMatter;
  }

  public MethodParenthesesHandler(final PsiMethod method,
                                  final boolean overloadsMatter,
                                  final boolean spaceBeforeParentheses, final boolean spaceBetweenParentheses,
                                  final boolean insertRightParenthesis) {
    super(spaceBeforeParentheses, spaceBetweenParentheses, insertRightParenthesis);
    myMethod = method;
    myOverloadsMatter = overloadsMatter;
  }

  protected boolean placeCaretInsideParentheses(final InsertionContext context, final LookupElement item) {
    return hasParams(item, context.getElements(), myOverloadsMatter, myMethod);
  }

  public static boolean hasParams(LookupElement item, LookupElement[] allItems, final boolean overloadsMatter, final PsiMethod method) {
    boolean hasParams = method.getParameterList().getParametersCount() > 0;
    if (overloadsMatter){
      hasParams |= hasOverloads(item, allItems, method);
    }
    return hasParams;
  }

  private static boolean hasOverloads(LookupElement item, LookupElement[] allItems, final PsiMethod method) {
    String name = method.getName();
    for (LookupElement item1 : allItems) {
      final Object o = item1.getObject();
      if (item.getObject() != o && o instanceof PsiMethod && ((PsiMethod)o).getName().equals(name)) {
        return true;
      }
    }
    return false;
  }

  @Override
  protected PsiElement findNextToken(final InsertionContext context) {
    PsiElement element = context.getFile().findElementAt(context.getTailOffset());
    if (element instanceof PsiWhiteSpace &&
        element.getText().contains("\n") &&
        !CodeStyleSettingsManager.getSettings(context.getProject()).METHOD_PARAMETERS_LPAREN_ON_NEXT_LINE) {
      return null;
    }

    return super.findNextToken(context);
  }

}
