package com.intellij.usages.rules;

import com.intellij.psi.PsiElement;
import com.intellij.usages.Usage;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 17, 2004
 * Time: 9:35:13 PM
 * To change this template use File | Settings | File Templates.
 */
public interface PsiElementUsage extends Usage {
  PsiElement getElement();
  boolean isNonCodeUsage();
}
