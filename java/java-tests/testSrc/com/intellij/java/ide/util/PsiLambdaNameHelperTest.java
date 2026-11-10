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
package com.intellij.java.ide.util;

import com.intellij.ide.util.PsiLambdaNameHelper;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiLambdaExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.Collection;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
public class PsiLambdaNameHelperTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNames() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final PsiClass aClass = myFixture.addClass("""
                                                 class Test {
                                                     Runnable r = () -> {
                                                     };
                                                     public void method() {
                                                         Runnable r = () -> {
                                                             Integer s = RedundantRename.this.s;
                                                             Runnable rr = () -> {};
                                                             new Runnable() {
                                                                 Runnable r1 = () -> {};
                                                                 @Override
                                                                 public void run() {}
                                                             };
                                                         };
                                                     }
                                                 }""");
    ReadAction.runBlocking(() -> {
      final Collection<PsiLambdaExpression> lambdaExpressions = PsiTreeUtil.findChildrenOfType(aClass, PsiLambdaExpression.class);
      final String[] expectedNames = {"lambda$new$0",
                                      "lambda$method$1",
                                      "lambda$method$2",
                                      "lambda$0"};
      int idx = 0;
      for (PsiLambdaExpression expression : lambdaExpressions) {
        assertEquals(expectedNames[idx++], PsiLambdaNameHelper.getVMName(expression));
      }
    });
  }
}