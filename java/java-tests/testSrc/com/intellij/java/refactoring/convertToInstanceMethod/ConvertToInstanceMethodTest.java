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
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.TargetElementUtil;
import com.intellij.java.refactoring.LightRefactoringTestCase;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodProcessor;
import com.intellij.util.VisibilityUtil;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public class ConvertToInstanceMethodTest extends LightRefactoringTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() { doTest(0); }

  public void testInterface() { doTest(1); }

  public void testInterfacePrivate() { doTest(1); }

  public void testInterface2() { doTest(0); }

  public void testInterface3() { doTest(0); }

  public void testTypeParameter() { doTest(0); }

  public void testInterfaceTypeParameter() { doTest(0); }

  public void testJavadocParameter() { doTest(0); }

  public void testConflictingParameterName() {
    doTest(0);
  }

  public void testVisibilityConflict() {
    try {
      doTest(0, PsiModifier.PRIVATE);
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>Test.foo(Bar)</code></b> is private and will not be accessible from instance initializer of class class <b><code>Test</code></b>.", e.getMessage()); 
    }
  }

  protected void doTest(final int targetParameter) {
    doTest(targetParameter, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  private void doTest(final int targetParameter, final String visibility) {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    final PsiElement targetElement = TargetElementUtil.findTargetElement(getEditor(), TargetElementUtil.ELEMENT_NAME_ACCEPTED);
    assertTrue("<caret> is not on method name", targetElement instanceof PsiMethod);
    PsiMethod method = (PsiMethod) targetElement;
    new ConvertToInstanceMethodProcessor(getProject(),
                                         method, targetParameter < 0 ? null : method.getParameterList().getParameters()[targetParameter],
                                         visibility).run();
    checkResultByFile(filePath + ".after");

  }

  protected String getBasePath() {
    return "/refactoring/convertToInstanceMethod/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }
}
