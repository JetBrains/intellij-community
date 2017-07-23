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
package com.intellij.java.codeInsight;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.generation.GenerateDelegateHandler;
import com.intellij.testFramework.LightCodeInsightTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author ven
 */
public class DelegateMethodsTest extends LightCodeInsightTestCase {

  private static final String BASE_PATH = "/codeInsight/delegateMethods/";

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testMethodWithJavadoc () throws Exception {
    doTest("1");
  }

  public void testStaticMemberWithNonStaticField() throws Exception {
    doTest();
  }

  public void testTypeParam() throws Exception {
    doTest();
  }

  public void testExistingMethodWithAnnotation() throws Exception {
    doTest();
  }

  public void testDelegateToContainingClassField() throws Exception {
    doTest();
  }
  
  public void testDelegateFromStaticClassField() throws Exception {
    doTest();
  }
  
  public void testCopyJavadoc() throws Exception {
    String testName = getTestName(false);
    configureByFile(BASE_PATH + "before" + testName + ".java");
    final GenerateDelegateHandler handler = new GenerateDelegateHandler();
    try {
      handler.setToCopyJavaDoc(true);
      handler.invoke(getProject(), getEditor(), getFile());
    }
    finally {
      handler.setToCopyJavaDoc(false);
    }
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }
  
  public void testSuperSubstitution() throws Exception {
    doTest();
  }
  
  public void testCopyAnnotationWithParams() throws Exception {
    doTest();
  }

  public void testMultipleOverrideAnnotations() throws Exception {
    doTest();
  }

  public void testStripSuppressWarningsAnnotation() throws Exception {
    doTest();
  }

  public void testDoNotOverrideFinal() throws Exception {
    doTest();
  }

  public void testAllowDelegateToFinal() throws Exception {
    doTest();
  }

  public void testDelegateWithSubstitutionOverrides() throws Exception {
    doTest();
  }

  public void testDelegateWithSubstitutionNoOverrides() throws Exception {
    doTest();
  }

  public void testSingleField() throws Exception {
    doTest();
  }

  public void testInsideLambdaWithNonInferredTypeParameters() throws Exception {
    doTest();
  }

  public void testTypeUseAnnotationsInReturnType() throws Exception {
    doTest();
  }

  public void testPreserveEllipsisType() throws Exception {
    doTest();
  }

  private void doTest() throws Exception {
    doTest(getTestName(false));
  }

  private void doTest(String testName) throws Exception {
    configureByFile(BASE_PATH + "before" + testName+ ".java");
    new GenerateDelegateHandler().invoke(getProject(), getEditor(), getFile());
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }
}
