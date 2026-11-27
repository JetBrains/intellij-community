// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.JavaTestUtil;
import com.intellij.codeInsight.HeavyJavaCodeInsightFixture;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaDocumentedElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtilBase;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.codeInsight.HeavyJavaCodeInsightFixture.heavyJavaCodeInsightFixture;
import static org.assertj.core.api.Assertions.assertThat;

@TestApplication
public class JavaDocumentationProviderCustomDocumentationElementTest {
  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/documentationprovider/";

  private final TestFixture<HeavyJavaCodeInsightFixture> heavyFixture = heavyJavaCodeInsightFixture(JavaTestUtil.getJavaTestDataPath());

  private JavaDocumentationProvider myProvider;

  @Test
  public void testClassClassKeyword() {
    doTestClass();
  }

  @Test
  public void testClassPublicKeyword() {
    doTestClass();
  }

  @Test
  public void testFieldFinalKeyword() {
    doTestClass();
  }

  @Test
  public void testFieldIntType() {
    doTestClass();
  }

  @Test
  public void testFieldPublicKeyword() {
    doTestClass();
  }

  @Test
  public void testFieldStaticKeyword() {
    doTestClass();
  }

  @Test
  public void testMethodPublicKeyword() {
    doTestClass();
  }

  @Test
  public void testMethodThrowsKeyword() {
    doTestClass();
  }

  @Test
  public void testMethodVoidKeyword() {
    doTestClass();
  }

  @Test
  public void testFieldStringType() {
    configure();
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    final String message = "The non-primitive type 'String' on a field can't be a custom documentation element";

    final PsiElement javadocCarrier = ReadAction.computeBlocking(() -> {
      final PsiElement fieldStringType = PsiUtilBase.getElementAtCaret(fixture.getEditor());
      return myProvider.getCustomDocumentationElement(fixture.getEditor(), fixture.getFile(), fieldStringType, 0);
    });

    assertThat(javadocCarrier).withFailMessage(message)
      .isNull();
  }

  private void doTestClass() {
    configure();
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();

    ReadAction.runBlocking(() -> {
      final PsiElement customJavadocElement = PsiUtilBase.getElementAtCaret(fixture.getEditor());
      final PsiJavaDocumentedElement expectedJavadocCarrier = PsiTreeUtil.getParentOfType(customJavadocElement, PsiJavaDocumentedElement.class);

      final String message = String.format("'%s' should be custom documentation element", customJavadocElement.getText());

      doTest(message, customJavadocElement, expectedJavadocCarrier);
    });
  }

  private void doTest(String message, PsiElement customJavadocElement, PsiElement expectedJavadocCarrier) {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final PsiElement javadocCarrier = myProvider.getCustomDocumentationElement(fixture.getEditor(), fixture.getFile(), customJavadocElement, 0);

    assertThat(javadocCarrier).withFailMessage(message)
      .isEqualTo(expectedJavadocCarrier);
  }

  @BeforeEach
  void setUp() {
    myProvider = new JavaDocumentationProvider();
  }

  private void configure() {
    HeavyJavaCodeInsightFixture fixture = heavyFixture.get();
    final String name = fixture.getTestName(false);
    fixture.configureByFile(TEST_DATA_FOLDER + name + ".java");
  }
}
