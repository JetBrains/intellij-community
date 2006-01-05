package com.intellij.codeInsight.lookup;

import com.intellij.psi.PsiElement;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Jun 20, 2005
 * Time: 8:12:52 PM
 * To change this template use File | Settings | File Templates.
 */
public interface LookupValueWithPsiElement {
  PsiElement getElement();
}
