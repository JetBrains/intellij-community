/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.java.psi.resolve;

import com.intellij.psi.*;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class ResolveVariable15Test extends LightResolveTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_1_5;
  }

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
    return findReferenceAtCaret("var15/" + getTestName(false) + ".java");
  }
}
