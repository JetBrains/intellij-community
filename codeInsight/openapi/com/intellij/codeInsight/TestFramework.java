/*
 * User: anna
 * Date: 28-May-2007
 */
package com.intellij.codeInsight;

import com.intellij.psi.PsiClass;

public interface TestFramework {
  boolean isTestKlass(PsiClass psiClass);
}