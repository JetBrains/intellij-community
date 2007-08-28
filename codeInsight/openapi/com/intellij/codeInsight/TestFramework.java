/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.codeInsight;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.util.IncorrectOperationException;

public interface TestFramework {
  boolean isTestKlass(PsiClass psiClass);
  PsiMethod findSetUpMethod(PsiClass psiClass) throws IncorrectOperationException;
}