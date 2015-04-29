/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.ResolveTestCase;

public class TypeInference18Test extends ResolveTestCase {

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
    PsiReference ref = configureByFile("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    PsiMethodReferenceExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodReferenceExpression.class, false);
    assertNotNull(methodCallExpression);
    assertNotNull(methodCallExpression.resolve());
  }

  private LanguageLevel myOldLanguageLevel;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myOldLanguageLevel = LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).getLanguageLevel();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_8);
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
  }

  @Override
  protected void tearDown() throws Exception {
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(myOldLanguageLevel);
    super.tearDown();
  }

  private void doTestMethodCall() throws Exception {
    PsiReference ref = configureByFile("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    PsiMethodCallExpression methodCallExpression = PsiTreeUtil.getParentOfType(ref.getElement(), PsiMethodCallExpression.class);
    assertNotNull(methodCallExpression);
    assertNotNull(methodCallExpression.resolveMethod());
  }

  private void doTest() throws Exception {
    PsiReference ref = configureByFile("/codeInsight/daemonCodeAnalyzer/lambda/resolve/" + getTestName(false) + ".java");
    assertNotNull(ref);
    assertNotNull(ref.resolve());
  }

  @Override
  protected Sdk getTestProjectJdk() {
    return IdeaTestUtil.getMockJdk18();
  }

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }
}
