// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.javadoc;

import com.intellij.codeInsight.JavaCodeInsightTestCase;
import com.intellij.lang.java.JavaDocumentationProvider;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;

import static org.assertj.core.api.Assertions.assertThat;

public class JavaDocumentationProviderCustomDocumentationElementTest extends JavaCodeInsightTestCase {
  private static final String TEST_DATA_FOLDER = "/codeInsight/javadocIG/documentationprovider/customDocumentationElements.java";

  private JavaDocumentationProvider myProvider;

  private PsiClass myClass;
  private PsiMethod myMethod;
  private PsiField myIntField;
  private PsiField myStringField;

  private PsiElement myStaticKeyword;
  private PsiElement myThrowsKeyword;
  private PsiElement myFinalKeyword;
  private PsiElement myClassKeyword;
  private PsiElement myIntFieldFinalKeyword;
  private PsiElement myIntFieldTypeKeyword;
  private PsiElement myStringFieldFinalKeyword;
  private PsiElement myStringFieldTypeKeyword;

  public void testMethodModifiers() {
    final String message = "'static' on a method should be a custom documentation element";
    doTest(message, myStaticKeyword, myMethod);
  }

  public void testMethodThrowsList() {
    final String message = "'throws' on a method should be a custom documentation element";

    doTest(message, myThrowsKeyword, myMethod);
  }

  public void testClassFinalKeyword() {
    final String message = "'final' on a class should be a custom documentation element";
    doTest(message, myFinalKeyword, myClass);
  }

  public void testClassKeyword() {
    final String message = "'class' on a class should be a custom documentation element";
    doTest(message, myClassKeyword, myClass);
  }

  public void testIntFieldFinalKeyword() {
    final String message = "'final' on a field should be a custom documentation element";
    doTest(message, myIntFieldFinalKeyword, myIntField);
  }

  public void testIntFieldTypeKeyword() {
    final String message = "The primitive type 'int' on a field should be a custom documentation element";
    doTest(message, myIntFieldTypeKeyword, myIntField);
  }

  public void testStringFieldFinalKeyword() {
    final String message = "'final' on a field should be a custom documentation element";
    doTest(message, myStringFieldFinalKeyword, myStringField);
  }

  public void testStringFieldTypeKeyword() {
    final String message = "The non-primitive type 'String' on a field can't be a custom documentation element";
    final PsiElement javadocCarrier = myProvider.getCustomDocumentationElement(myEditor, myFile, myStringFieldTypeKeyword, 0);

    assertThat(javadocCarrier).withFailMessage(message)
      .isNull();
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    configureByFile(TEST_DATA_FOLDER);
    myProvider = new JavaDocumentationProvider();

    myClass = ((PsiJavaFile)myFile).getClasses()[0];
    myMethod = myClass.getMethods()[0];
    final PsiModifierList methodModifierList = myMethod.getModifierList();
    myStaticKeyword = PsiTreeUtil.findChildOfType(methodModifierList, PsiKeyword.class);
    final PsiJavaCodeReferenceElement throwsElement = myMethod.getThrowsList().getReferenceElements()[0];
    myThrowsKeyword = PsiTreeUtil.getPrevSiblingOfType(throwsElement, PsiKeyword.class);

    myFinalKeyword = PsiTreeUtil.findChildOfType(myClass.getModifierList(), PsiKeyword.class);

    myClassKeyword = PsiTreeUtil.skipSiblingsForward(myFinalKeyword.getParent(), PsiWhiteSpace.class);

    final PsiField[] fields = myClass.getFields();
    myIntField = fields[0];
    final PsiModifierList intFieldModifierList = myIntField.getModifierList();
    myIntFieldFinalKeyword = PsiTreeUtil.findChildOfType(intFieldModifierList, PsiKeyword.class);
    final PsiTypeElement type = PsiTreeUtil.findChildOfType(myIntField, PsiTypeElement.class);
    myIntFieldTypeKeyword = PsiTreeUtil.findChildOfType(type, PsiKeyword.class);

    myStringField = fields[1];
    final PsiModifierList stringFieldModifierList = myStringField.getModifierList();
    myStringFieldFinalKeyword = PsiTreeUtil.findChildOfType(stringFieldModifierList, PsiKeyword.class);
    final PsiTypeElement type1 = PsiTreeUtil.findChildOfType(myStringField, PsiTypeElement.class);
    myStringFieldTypeKeyword = PsiTreeUtil.findChildOfType(type1, PsiIdentifier.class);
  }

  private void doTest(String message, PsiElement customJavadocElement, PsiElement expectedJavadocCarrier) {
    final PsiElement javadocCarrier = myProvider.getCustomDocumentationElement(myEditor, myFile, customJavadocElement, 0);

    assertThat(javadocCarrier).withFailMessage(message)
      .isEqualTo(expectedJavadocCarrier);
  }
}
