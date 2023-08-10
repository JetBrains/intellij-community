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
package com.intellij.java.psi.impl.source.tree.java;

import com.intellij.java.psi.GenericsTestCase;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleSettings;
import com.intellij.psi.search.GlobalSearchScope;

public class BindToGenericClassTest extends GenericsTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    setupGenericSampleClasses();
    JavaCodeStyleSettings.getInstance(getProject()).USE_FQ_CLASS_NAMES = true;
  }

  public void testReferenceElement() {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiClass classA = manager.getElementFactory().createClassFromText("class A extends List<String>{}", null).getInnerClasses()[0];
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    classA.getExtendsList().getReferenceElements()[0].bindToElement(classTestList);
    assertEquals("class A extends test.List<String>{}", classA.getText());
  }

  public void testReference() {
    final JavaPsiFacade manager = getJavaFacade();
    final PsiExpression psiExpression = manager.getElementFactory().createExpressionFromText("List", null);
    final PsiClass classTestList = manager.findClass("test.List", GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(myModule));
    assertNotNull(classTestList);
    final PsiElement result = ((PsiReferenceExpression) psiExpression).bindToElement(classTestList);
    assertEquals("test.List", result.getText());
  }
}
