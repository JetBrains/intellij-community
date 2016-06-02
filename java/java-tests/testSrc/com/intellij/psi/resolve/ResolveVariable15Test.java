/*
 * Copyright (c) 2006 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.psi.resolve;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;

/**
 * @author ven
 */
public class ResolveVariable15Test extends Resolve15TestCase {

  public void testDuplicateStaticImport() throws Exception {
    resolveTarget();
  }

  public void testRhombExtending() throws Exception {
    resolveTarget();
  }

  private PsiElement resolveTarget() throws Exception {
    PsiReference ref = configure();
    final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
    PsiElement target = result.getElement();
    assertNotNull(target);
    assertTrue(result.isValidResult());
    return target;
  }

  public void testNavigateToEnumFunction() throws Exception {
    PsiElement element = resolveTarget();
    assertTrue(element instanceof PsiMethod);
    PsiClass aClass = ((PsiMethod)element).getContainingClass();
    assertTrue(aClass instanceof PsiEnumConstantInitializer);
    SearchScope scope = element.getUseScope();
    assertFalse(scope instanceof LocalSearchScope);
  }

  private PsiReference configure() throws Exception {
    return configureByFile("var15/" + getTestName(false) + ".java");
  }
}
