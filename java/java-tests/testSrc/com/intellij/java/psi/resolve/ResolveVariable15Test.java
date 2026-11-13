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

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.JavaResolveResult;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiEnumConstantInitializer;
import com.intellij.psi.PsiJavaReference;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ResolveVariable15Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_5);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture);

  @Test
  public void testDuplicateStaticImport() throws Exception {
    resolveTarget();
  }

  @Test
  public void testRhombExtending() throws Exception {
    resolveTarget();
  }

  private PsiElement resolveTarget() throws Exception {
    PsiReference ref = configure();
    return ReadAction.computeBlocking(() -> {
      final JavaResolveResult result = ((PsiJavaReference)ref).advancedResolve(true);
      PsiElement target = result.getElement();
      assertNotNull(target);
      assertTrue(result.isValidResult());
      return target;
    });
  }

  @Test
  public void testNavigateToEnumFunction() throws Exception {
    PsiElement element = resolveTarget();
    ReadAction.runBlocking(() -> {
      assertTrue(element instanceof PsiMethod);
      PsiClass aClass = ((PsiMethod)element).getContainingClass();
      assertTrue(aClass instanceof PsiEnumConstantInitializer);
      SearchScope scope = element.getUseScope();
      assertFalse(scope instanceof LocalSearchScope);
    });
  }

  private PsiReference configure() throws Exception {
    return resolveFixture.get().findReferenceAtCaret("var15/" + resolveFixture.get().getTestName(false) + ".java");
  }
}
