package com.intellij.psi.filters.getters;

import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.filters.ClassFilter;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.codeInsight.completion.CompletionContext;

import java.util.Set;
import java.util.HashSet;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:29:59
 * To change this template use Options | File Templates.
 */
public class CastTypeGetter implements ContextGetter{
  public Object[] get(PsiElement context, CompletionContext completionContext){
    final PsiTypeCastExpression cast = PsiTreeUtil.getContextOfType(context, PsiTypeCastExpression.class, true);
    return new Object[]{cast.getCastType().getType()};
  }
}
