// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaDocumentationProviderCustomDocumentationElementTest extends JavaCodeInsightTestCase {
  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/documentationprovider/";

  private JavaDocumentationProvider myProvider;

  public void testClassClassKeyword() throws Exception {
    doTestClass();
  }

  public void testClassPublicKeyword() throws Exception {
    doTestClass();
  }

  public void testFieldFinalKeyword() throws Exception {
    doTestClass();
  }

  public void testFieldIntType() throws Exception {
    doTestClass();
  }

  public void testFieldPublicKeyword() throws Exception {
    doTestClass();
  }

  public void testFieldStaticKeyword() throws Exception {
    doTestClass();
  }

  public void testMethodPublicKeyword() throws Exception {
    doTestClass();
  }

  public void testMethodThrowsKeyword() throws Exception {
    doTestClass();
  }

  public void testMethodVoidKeyword() throws Exception {
    doTestClass();
  }

  public void testFieldStringType() throws Exception {
    configure();

    final PsiElement fieldStringType = PsiUtilBase.getElementAtCaret(getEditor());
    final String message = "The non-primitive type 'String' on a field can't be a custom documentation element";

    final PsiElement javadocCarrier = myProvider.getCustomDocumentationElement(myEditor, myFile, fieldStringType, 0);

    assertThat(javadocCarrier).withFailMessage(message)
      .isNull();
  }

  private void doTestClass() throws Exception {
    configure();

    final PsiElement customJavadocElement = PsiUtilBase.getElementAtCaret(getEditor());
    final PsiJavaDocumentedElement expectedJavadocCarrier = PsiTreeUtil.getParentOfType(customJavadocElement, PsiJavaDocumentedElement.class);

    final String message = String.format("'%s' should be custom documentation element", customJavadocElement.getText());

    doTest(message, customJavadocElement, expectedJavadocCarrier);
  }

  private void doTest(String message, PsiElement customJavadocElement, PsiElement expectedJavadocCarrier) {
    final PsiElement javadocCarrier = myProvider.getCustomDocumentationElement(myEditor, myFile, customJavadocElement, 0);

    assertThat(javadocCarrier).withFailMessage(message)
      .isEqualTo(expectedJavadocCarrier);
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myProvider = new JavaDocumentationProvider();
  }

  private void configure() throws Exception {
    final String name = getTestName(false);
    configureByFile(TEST_DATA_FOLDER + name + ".java");
  }
}
