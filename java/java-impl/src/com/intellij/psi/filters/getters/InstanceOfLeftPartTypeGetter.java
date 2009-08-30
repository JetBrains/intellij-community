package com.intellij.psi.filters.getters;

import com.intellij.psi.*;
import com.intellij.psi.filters.FilterUtil;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 15.12.2003
 * Time: 17:39:34
 * To change this template use Options | File Templates.
 */
public class InstanceOfLeftPartTypeGetter {
  public static PsiType[] getLeftTypes(PsiElement context) {
    if((context = FilterUtil.getPreviousElement(context, true)) == null) return PsiType.EMPTY_ARRAY;
    if(!PsiKeyword.INSTANCEOF.equals(context.getText())) return PsiType.EMPTY_ARRAY;
    if((context = FilterUtil.getPreviousElement(context, false)) == null) return PsiType.EMPTY_ARRAY;

    final PsiExpression contextOfType = PsiTreeUtil.getContextOfType(context, PsiExpression.class, false);
    if (contextOfType == null) return PsiType.EMPTY_ARRAY;

    PsiType type = contextOfType.getType();
    if (type == null) return PsiType.EMPTY_ARRAY;

    if (type instanceof PsiClassType) {
      final PsiClass psiClass = ((PsiClassType)type).resolve();
      if (psiClass instanceof PsiTypeParameter) {
        return psiClass.getExtendsListTypes();
      }
    }

    return new PsiType[]{type};
  }
}
