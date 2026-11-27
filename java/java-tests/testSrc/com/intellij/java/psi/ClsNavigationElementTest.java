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

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiCompiledElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

@TestApplication
public class ClsNavigationElementTest {
  // 1.7 mock has sources attached; necessary in this test
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_7);

  @Test
  public void testNavigationElements() {
    Project project = projectFixture.get().getProject();
    ReadAction.runBlocking(() -> {
      String className = CommonClassNames.JAVA_LANG_STRING;
      PsiClass clsClass = JavaPsiFacade.getInstance(project).findClass(className, GlobalSearchScope.allScope(project));
      assertNotNull(clsClass);
      assertInstanceOf(PsiCompiledElement.class, clsClass);
      assertEquals(className, clsClass.getQualifiedName());
    
      PsiClass psiClass = (PsiClass)clsClass.getNavigationElement();
      assertNotNull(psiClass);
      assertFalse(psiClass instanceof PsiCompiledElement);
      assertEquals(className, psiClass.getQualifiedName());

      String fieldName = "CASE_INSENSITIVE_ORDER";
      PsiField clsField = clsClass.findFieldByName(fieldName, false);
      assertNotNull(clsField);
      assertInstanceOf(PsiCompiledElement.class, clsField);
      assertEquals(fieldName, clsField.getName());

      PsiField psiField = (PsiField)clsField.getNavigationElement();
      assertNotNull(psiField);
      assertFalse(psiField instanceof PsiCompiledElement);
      assertEquals(fieldName, psiField.getName());

      String methodName = "equalsIgnoreCase";
      PsiMethod[] methods = clsClass.findMethodsByName(methodName, false);
      assertEquals(1, methods.length);
      PsiMethod clsMethod = methods[0];
      assertInstanceOf(PsiCompiledElement.class, clsMethod);
      assertEquals(methodName, clsMethod.getName());
    
      PsiMethod psiMethod = (PsiMethod)clsMethod.getNavigationElement();
      assertNotNull(psiMethod);
      assertFalse(psiMethod instanceof PsiCompiledElement);
      assertEquals(methodName, psiMethod.getName());

      PsiParameter[] clsParameters = clsMethod.getParameterList().getParameters();
      assertEquals(1, clsParameters.length);
      PsiParameter clsParameter = clsParameters[0];
      assertInstanceOf(PsiCompiledElement.class, clsParameter);

      PsiParameter psiParameter = (PsiParameter)clsParameter.getNavigationElement();
      assertNotNull(psiParameter);
      assertFalse(psiParameter instanceof PsiCompiledElement);
      assertEquals(clsParameter.getName(), psiParameter.getName());
    
      PsiParameter[] psiParameters = psiMethod.getParameterList().getParameters();
      assertSame(psiParameters[0], psiParameter);
    });
  }
}