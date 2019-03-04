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
import com.intellij.psi.PsiMethodCallExpression;
import com.intellij.psi.PsiMethodReferenceExpression;
import com.intellij.psi.PsiReference;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.LightResolveTestCase;
import org.jetbrains.annotations.NotNull;

public class TypeInference18Test extends LightResolveTestCase {

  public void testIDEA122406() throws Exception {
    doTest();
  }

  public void testSecondConflictResolution() throws Exception {
    doTestMethodCall();
  }

  public void testSecondConflictResolution1() throws Exception {
    doTestMethodCall();
  }

  public void testSecondConflictResolution2() throws Exception {
    doTestMethodCall();
  }

  public void testLambdaChainConflictResolution() throws Exception {
    doTestMethodCall();
  }

  public void testCachedSubstitutionDuringOverloadResolution() throws Exception {
    PsiReference ref = findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    PsiMethodReferenceExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodReferenceExpression.class, false);
    assertNotNull(methodCallExpression);
    assertNotNull(methodCallExpression.resolve());
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTestMethodCall() throws Exception {
    PsiReference ref = findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodCallExpression.class);
    assertNotNull(methodCallExpression);
    assertNotNull(methodCallExpression.resolveMethod());
  }

  private void doTest() throws Exception {
    PsiReference ref = findReferenceAtCaret("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    assertNotNull(ref.resolve());
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
