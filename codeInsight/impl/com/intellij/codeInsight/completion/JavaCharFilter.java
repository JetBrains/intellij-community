/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Jul 23, 2002
 * Time: 3:01:22 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.codeInsight.completion;

import com.intellij.codeInsight.lookup.CharFilter;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupItem;
import com.intellij.psi.*;

public class JavaCharFilter extends CharFilter {

  private static boolean isWithinLiteral(final Lookup lookup) {
    PsiElement psiElement = lookup.getPsiElement();
    return psiElement != null && psiElement.getParent() instanceof PsiLiteralExpression;
  }

  public Result acceptChar(char c, final int prefixLength, final Lookup lookup) {
    if (lookup.isCompletion() && c == '!') {
      if (lookup.getPsiFile() instanceof PsiJavaFile) {
        final LookupItem item = lookup.getCurrentItem();
        if (item == null) return null;

        final Object o = item.getObject();
        if (o instanceof PsiVariable) {
          if (PsiType.BOOLEAN.isAssignableFrom(((PsiVariable)o).getType())) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
        }
        if (o instanceof PsiMethod) {
          final PsiType type = ((PsiMethod)o).getReturnType();
          if (type != null && PsiType.BOOLEAN.isAssignableFrom(type)) return Result.SELECT_ITEM_AND_FINISH_LOOKUP;
        }

        return null;
      }
    }
    if (lookup.isCompletion() && c == '.' && isWithinLiteral(lookup)) return Result.ADD_TO_PREFIX;
    return null;
  }

}