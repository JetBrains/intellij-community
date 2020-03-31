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
package com.intellij.java.psi;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.LightPlatformTestCase;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class ClsNavigationElementTest extends LightPlatformTestCase {
  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_1_7; // 1.7 mock has sources attached; necessary in this test
  }
  
  public void testNavigationElements() {
    String className = CommonClassNames.JAVA_LANG_STRING;
    PsiClass clsClass = JavaPsiFacade.getInstance(getProject()).findClass(className, GlobalSearchScope.allScope(getProject()));
    assertNotNull(clsClass);
    assertInstanceOf(clsClass, PsiCompiledElement.class);
    assertEquals(className, clsClass.getQualifiedName());
    
    PsiClass psiClass = (PsiClass)clsClass.getNavigationElement();
    assertNotNull(psiClass);
    assertFalse(psiClass instanceof PsiCompiledElement);
    assertEquals(className, psiClass.getQualifiedName());

    String fieldName = "CASE_INSENSITIVE_ORDER";
    PsiField clsField = clsClass.findFieldByName(fieldName, false);
    assertNotNull(clsField);
    assertInstanceOf(clsField, PsiCompiledElement.class);
    assertEquals(fieldName, clsField.getName());

    PsiField psiField = (PsiField)clsField.getNavigationElement();
    assertNotNull(psiField);
    assertFalse(psiField instanceof PsiCompiledElement);
    assertEquals(fieldName, psiField.getName());

    String methodName = "equalsIgnoreCase";
    PsiMethod[] methods = clsClass.findMethodsByName(methodName, false);
    assertEquals(1, methods.length);
    PsiMethod clsMethod = methods[0];
    assertInstanceOf(clsMethod, PsiCompiledElement.class);
    assertEquals(methodName, clsMethod.getName());
    
    PsiMethod psiMethod = (PsiMethod)clsMethod.getNavigationElement();
    assertNotNull(psiMethod);
    assertFalse(psiMethod instanceof PsiCompiledElement);
    assertEquals(methodName, psiMethod.getName());

    PsiParameter[] clsParameters = clsMethod.getParameterList().getParameters();
    assertEquals(1, clsParameters.length);
    PsiParameter clsParameter = clsParameters[0];
    assertInstanceOf(clsParameter, PsiCompiledElement.class);

    PsiParameter psiParameter = (PsiParameter)clsParameter.getNavigationElement();
    assertNotNull(psiParameter);
    assertFalse(psiParameter instanceof PsiCompiledElement);
    assertEquals(clsParameter.getName(), psiParameter.getName());
    
    PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
    assertSame(psiParameters[0], psiParameter);
  }
}