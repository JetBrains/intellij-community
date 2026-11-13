/*
 * Copyright 2000-2018 JetBrains s.r.o.
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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightResolveFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.LightResolveFixtureKt.lightResolveFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class TypeInference18Test {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_8);
  private final TestFixture<LightResolveFixture> resolveFixture = lightResolveFixture(projectFixture, JavaTestUtil.getJavaTestDataPath());

  @Test
  public void testIDEA122406() throws Exception {
    doTest();
  }

  @Test
  public void testSecondConflictResolution() throws Exception {
    doTestMethodCall();
  }

  @Test
  public void testSecondConflictResolution1() throws Exception {
    doTestMethodCall();
  }

  @Test
  public void testSecondConflictResolution2() throws Exception {
    doTestMethodCall();
  }

  @Test
  public void testLambdaChainConflictResolution() throws Exception {
    doTestMethodCall();
  }

  @Test
  public void testCachedSubstitutionDuringOverloadResolution() throws Exception {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      assertNotNull(ref);
      PsiMethodReferenceExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodReferenceExpression.class, false);
      assertNotNull(methodCallExpression);
      assertNotNull(methodCallExpression.resolve());
    });
  }

  private void doTestMethodCall() throws Exception {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      assertNotNull(ref);
      PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodCallExpression.class);
      assertNotNull(methodCallExpression);
      assertNotNull(methodCallExpression.resolveMethod());
    });
  }

  private void doTest() throws Exception {
    PsiReference ref = resolveFixture.get().findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + resolveFixture.get().getTestName(false) + ".java");
    ReadAction.runBlocking(() -> {
      assertNotNull(ref);
      assertNotNull(ref.resolve());
    });
  }
}
