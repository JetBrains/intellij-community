// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.refactoring.convertToInstanceMethod;

import com.intellij.JavaTestUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiIdentifier;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameter;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.BaseRefactoringProcessor;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodHandler;
import com.intellij.refactoring.convertToInstanceMethod.ConvertToInstanceMethodProcessor;
import com.intellij.testFramework.LightJavaCodeInsightTestCase;
import com.intellij.util.VisibilityUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;

public class ConvertToInstanceMethodTest extends LightJavaCodeInsightTestCase {
  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath();
  }

  public void testSimple() { doTest(0); }

  public void testInterface() { doTest(0); }

  public void testInterfacePrivate() { doTest(0); }

  public void testInterface2() { doTest(0); }

  public void testInterface3() { doTest(0); }

  public void testTypeParameter() { doTest(0); }

  public void testInterfaceTypeParameter() { doTest(0); }

  public void testJavadocParameter() { doTest(0); }

  public void testConflictingParameterName() { doTest(0); }

  public void testVisibilityConflict() {
    try {
      doTest(0, PsiModifier.PRIVATE);
      fail("Conflict was not detected");
    }
    catch (BaseRefactoringProcessor.ConflictsInTestsException e) {
      assertEquals("Method <b><code>foo(Bar)</code></b> is private and will not be accessible from instance initializer of class " +
                   "<b><code>Test</code></b>.", e.getMessage());
    }
  }

  protected void doTest(int targetParameter) {
    doTest(targetParameter, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  protected void doTest(int targetParameter, String visibility, String... options) {
    final String filePath = getBasePath() + getTestName(false) + ".java";
    configureByFile(filePath);
    callRefactoring(targetParameter, visibility, options);
    checkResultByFile(filePath + ".after");
  }

  private void callRefactoring(int parameter, String visibility, String... options) {
    Editor editor = getEditor();
    PsiElement element = getFile().findElementAt(editor.getCaretModel().getOffset());
    if (element == null) fail();
    if (element instanceof PsiIdentifier) element = element.getParent();
    if (!(element instanceof PsiMethod method)) {
      fail();
      return;
    }

    Object[] objects = ConvertToInstanceMethodHandler.calculatePossibleInstanceQualifiers(method);
    if (options.length > 0) {
      List<Object> convertedParameters = ContainerUtil.map(objects, ob ->
        ob instanceof PsiVariable variable ?
        PsiFormatUtil.formatVariable(variable,
                                     PsiFormatUtilBase.SHOW_NAME |
                                     PsiFormatUtilBase.SHOW_TYPE,
                                     PsiSubstitutor.EMPTY) : ob);
      assertEquals(Arrays.toString(options), Arrays.toString(convertedParameters.toArray()));
    }

    final ConvertToInstanceMethodProcessor processor =
      new ConvertToInstanceMethodProcessor(
        method.getProject(),
        method,
        objects[parameter] instanceof PsiParameter psiParameter ? psiParameter : null,
        visibility);
    processor.run();
  }

  protected void doTestException() {
    configureByFile(getBasePath() + getTestName(false) + ".java");
    callRefactoring(0, VisibilityUtil.ESCALATE_VISIBILITY);
  }

  protected String getBasePath() {
    return "/refactoring/convertToInstanceMethod/";
  }

  @Override
  protected LanguageLevel getLanguageLevel() {
    return LanguageLevel.JDK_1_6;
  }
}
