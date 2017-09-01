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

  public void testMethodWithJavadoc() { doTest(); }
  public void testStaticMemberWithNonStaticField() { doTest(); }
  public void testTypeParam() { doTest(); }
  public void testExistingMethodWithAnnotation() { doTest(); }
  public void testDelegateToContainingClassField() { doTest(); }
  public void testDelegateFromStaticClassField() { doTest(); }

  public void testCopyJavadoc() {
    String testName = getTestName(false);
    configureByFile(BASE_PATH + "before" + testName + ".java");
    GenerateDelegateHandler handler = new GenerateDelegateHandler();
    try {
      handler.setToCopyJavaDoc(true);
      handler.invoke(getProject(), getEditor(), getFile());
    }
    finally {
      handler.setToCopyJavaDoc(false);
    }
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }

  public void testSuperSubstitution() { doTest(); }
  public void testCopyAnnotationWithParams() { doTest(); }
  public void testMultipleOverrideAnnotations() { doTest(); }
  public void testStripSuppressWarningsAnnotation() { doTest(); }
  public void testDoNotOverrideFinal() { doTest(); }
  public void testAllowDelegateToFinal() { doTest(); }
  public void testDelegateWithSubstitutionOverrides() { doTest(); }
  public void testDelegateWithSubstitutionNoOverrides() { doTest(); }
  public void testSingleField() { doTest(); }
  public void testInsideLambdaWithNonInferredTypeParameters() { doTest(); }
  public void testTypeUseAnnotationsInReturnType() { doTest(); }
  public void testTypeUseAnnotationsInArrayParameter() { doTest(); }
  public void testPreserveEllipsisType() { doTest(); }

  private void doTest() {
    String testName = getTestName(false);
    configureByFile(BASE_PATH + "before" + testName + ".java");
    new GenerateDelegateHandler().invoke(getProject(), getEditor(), getFile());
    checkResultByFile(BASE_PATH + "after" + testName + ".java");
  }
}