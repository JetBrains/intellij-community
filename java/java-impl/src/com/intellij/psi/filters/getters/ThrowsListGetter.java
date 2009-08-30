package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiType;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:29:59
 * To change this template use Options | File Templates.
 */
public class ThrowsListGetter implements ContextGetter{
  public PsiType[] get(PsiElement context, CompletionContext completionContext){
    final PsiMethod method = PsiTreeUtil.getContextOfType(context, PsiMethod.class, true);
    if(method != null){
      return method.getThrowsList().getReferencedTypes();
    }
    return PsiType.EMPTY_ARRAY;
  }
}
