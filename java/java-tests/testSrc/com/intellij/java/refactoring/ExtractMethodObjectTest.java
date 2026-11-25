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

package com.intellij.java.refactoring;

import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectHandler;
import com.intellij.refactoring.extractMethodObject.ExtractMethodObjectProcessor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaTestDescriptor;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

@TestApplication
public class ExtractMethodObjectTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(lightJavaTestDescriptor());
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture =
    lightJavaCodeInsightFixture(projectFixture, "/java/java-tests/testData");

  private void doTest() throws Exception {
    doTest(true);
  }

  private void doTest(final boolean createInnerClass) throws Exception {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    final String testName = myFixture.getTestName(false);
    myFixture.configureByFile("/refactoring/extractMethodObject/" + testName + ".java");
    myFixture.runInCommand(() -> {
      PsiElement element = TargetElementUtil.findTargetElement(myFixture.getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
      assertInstanceOf(PsiMethod.class, element);
      final PsiMethod method = (PsiMethod) element;

      final ExtractMethodObjectProcessor processor =
        new ExtractMethodObjectProcessor(myFixture.getProject(), myFixture.getEditor(), method.getBody().getStatements(), "InnerClass");
      final ExtractMethodObjectProcessor.MyExtractMethodProcessor extractProcessor = processor.getExtractProcessor();
      processor.setCreateInnerClass(createInnerClass);
      extractProcessor.setShowErrorDialogs(false);
      extractProcessor.prepare();
      extractProcessor.testPrepare();

      ExtractMethodObjectHandler.extractMethodObject(myFixture.getProject(), myFixture.getEditor(), processor, extractProcessor);

      myFixture.checkResultByFile("/refactoring/extractMethodObject/" + testName + ".java" + ".after");
    });
  }

  @Test
  public void testStatic() throws Exception {
    doTest();
  }

  @Test
  public void testStaticTypeParams() throws Exception {
    doTest();
  }

  @Test
  public void testStaticTypeParamsReturn() throws Exception {
    doTest();
  }

  @Test
  public void testTypeParamsReturn() throws Exception {
    doTest();
  }

  @Test
  public void testTypeParams() throws Exception {
    doTest();
  }

  @Test
  public void testMethodInHierarchy() throws Exception {
    doTest();
  }

  @Test
  public void testQualifier() throws Exception {
    doTest();
  }

  @Test
  public void testVarargs() throws Exception {
    doTest();
  }

  @Test
  public void testFieldUsage() throws Exception {
    doTest();
  }

  @Test
  public void testMethodInHierarchyReturn() throws Exception {
    doTest();
  }

  @Test
  public void testStaticTypeParamsReturnNoDelete() throws Exception {
    doTest();
  }

  @Test
  public void testStaticTypeParamsRecursive() throws Exception {
    doTest();
  }

  @Test
  public void testRecursion() throws Exception {
    doTest();
  }

  @Test
  public void testWrapWithObject() throws Exception {
    doTest(false);
  }

  @Test
  public void testWrapWithObjectRecursive() throws Exception {
    doTest(false);
  }
  
  @Test
  public void testWithPrivateMethodUsed() throws Exception {
    doTest();
  }
  
  @Test
  public void testWithPrivateMethodUsed1() throws Exception {
    doTest();
  }

  @Test
  public void testWithPrivateStaticMethodUsed() throws Exception {
    doTest();
  }

  @Test
  public void testWithPrivateStaticMethodUsed2() throws Exception {
    doTest();
  }

  @Test
  public void testThisAndSuperAnon() throws Exception {
    doTest(false);
  }

  @Test
  public void testWithPrivateMethodWhichCantBeMoved() throws Exception {
    doTest();
  }
}
