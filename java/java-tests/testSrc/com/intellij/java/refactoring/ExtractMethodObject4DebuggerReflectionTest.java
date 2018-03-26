// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring;

import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.psi.JavaCodeFragment;
import com.intellij.psi.JavaCodeFragmentFactory;
import com.intellij.psi.PsiElement;
import com.intellij.refactoring.extractMethod.PrepareFailedException;
import com.intellij.refactoring.extractMethodObject.ExtractLightMethodObjectHandler;
import com.intellij.testFramework.UsefulTestCase;
import org.jetbrains.annotations.NotNull;

/**
 * @author Vitaliy.Bibaev
 */
public class ExtractMethodObject4DebuggerReflectionTest extends LightRefactoringTestCase {
  public void testAccessField() throws PrepareFailedException {
    doTest("System.out.println(instance.field)");
  }

  public void testUpdateField() throws PrepareFailedException {
    doTest("instance.field = 50");
  }

  public void testAccessConstructor() throws PrepareFailedException {
    doTest("new WithReflectionAccess(50)");
  }

  public void testAccessMethod() throws PrepareFailedException {
    doTest("method()");
  }

  public void testAccessMethodReference() throws PrepareFailedException {
    doTest("apply(WithReflectionAccess::method)");
  }

  public void testTwiceAccessToTheSameField() throws PrepareFailedException {
    doTest("instance.field + instance.field");
  }

  public void testMethodWithParameter() throws PrepareFailedException {
    doTest("instance.method(instance)");
  }

  public void testMethodWithPrimitiveParameter() throws PrepareFailedException {
    doTest("instance.method(42)");
  }

  public void testCallDefaultConstructor() throws PrepareFailedException {
    doTest("new Inner()");
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return super.getTestDataPath() + "/refactoring/extractMethodObject4Debugger";
  }

  private void doTest(String evaluatedText) throws PrepareFailedException {
    String testName = getTestName(true);
    configureByFile("/WithReflectionAccess.java");
    final int offset = getEditor().getCaretModel().getOffset();
    final PsiElement context = getFile().findElementAt(offset);
    final JavaCodeFragmentFactory fragmentFactory = JavaCodeFragmentFactory.getInstance(getProject());
    final JavaCodeFragment fragment = fragmentFactory.createExpressionCodeFragment(evaluatedText, context, null, false);
    final ExtractLightMethodObjectHandler.ExtractedData extractedData =
      ExtractLightMethodObjectHandler.extractLightMethodObject(getProject(), context, fragment, "test", JavaSdkVersion.JDK_1_9);
    assertNotNull(extractedData);
    assertFalse(extractedData.useMagicAccessor());
    String actualText = "call text: " + extractedData.getGeneratedCallText() + "\n" +
                        "class: " + "\n" +
                        extractedData.getGeneratedInnerClass().getText();
    UsefulTestCase
      .assertSameLinesWithFile(getTestDataPath() + "/outs/" + testName + ".out", actualText, true);
  }
}
