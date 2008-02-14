package com.intellij.psi.filters;

import com.intellij.psi.PsiElement;
import com.intellij.codeInsight.completion.CompletionContext;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 20.03.2003
 * Time: 21:14:27
 * To change this template use Options | File Templates.
 */
public interface ContextGetter{
  Object[] get(final PsiElement context, CompletionContext completionContext);
}
