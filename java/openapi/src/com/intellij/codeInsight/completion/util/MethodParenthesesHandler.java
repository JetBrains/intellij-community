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
