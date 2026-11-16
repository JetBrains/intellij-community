// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring;

import com.intellij.openapi.application.ReadAction;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.refactoring.LightMultiFileFixtureKt.lightMultiFileFixture;
import static com.intellij.refactoring.TypeMigrationTestUtil.doTestFirstParamType;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class WildcardTypeMigrationTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_1_6);
  private final TestFixture<LightMultiFileFixture> multiFile =
    lightMultiFileFixture(projectFixture, TypeMigrationTestUtil.getTestDataPath() + "/refactoring/wildcard/");

  @Test
  public void testProducerExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null)));
  }

  @Test
  public void testProducerSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null)));
  }

  @Test
  public void testProducerUnbounded() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<?>", null)));
  }

  @Test
  public void testProducerCollectionChanged() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? super java.lang.Integer>", null)));
  }

  @Test
  public void testProducerExtendsCollectionChanged() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.Set<? extends java.lang.Object>", null)));
  }

  @Test
  public void testProducerStopAtWildcard() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.List<? super java.lang.Number>", null)));
  }

  @Test
  public void testProducerFailToStopAtWildcard() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.List<? super java.lang.Integer>", null)));
  }

  @Test
  public void testProducerExtendsFailToStopAtWildcard() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.List<? extends java.lang.Number>", null)));
  }

  @Test
  public void testConsumerExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? extends java.lang.Number>", null)));
  }

  @Test
  public void testConsumerSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? super java.lang.Number>", null)));
  }

  @Test
  public void testConsumerUnbounded() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<?>", null)));
  }

  // array -> list
  @Test
  public void testAssignmentExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null)));
  }

  @Test
  public void testAssignmentSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null)));
  }

  @Test
  public void testAssignmentUnbounded() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<?>", null)));
  }

  @Test
  public void testGetExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? extends java.lang.Integer>", null)));
  }

  @Test
  public void testGetSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<? super java.lang.Integer>", null)));
  }

  @Test
  public void testGetUnbounded() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<?>", null)));

  }

  @Test
  public void testLengthSize() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.util.ArrayList<?>", null)));

  }

  //list -> array
  @Test
  public void testGetAssignmentExtendsToType() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Number", null).createArrayType()));
  }

  @Test
  public void testGetAssignmentExtendsToSuperType() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText(CommonClassNames.JAVA_LANG_OBJECT, null).createArrayType()));
  }

  @Test
  public void testGetAssignmentExtendsToChildType() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.Integer", null).createArrayType()));
  }

  // -> threadlocal with wildcard
  @Test
  public void testThreadLocalProducerExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<java.util.List<? extends String>>", null)));
  }

  //List<? super String> is not assignable to List<String> though it is possible to pass string where ? super String was   
  @Test
  public void testThreadLocalProducerSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<java.util.List<? super String>>", null)));
  }

  @Test
  public void testThreadLocalConsumerSuper() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<? super String>", null)));
  }

  @Test
  public void testThreadLocalConsumerExtends() {
    LightJavaCodeInsightTestFixture myFixture = multiFile.get().getFixture();
    doTestFirstParamType(multiFile.get(), "method",
                         ReadAction.computeBlocking(() -> JavaPsiFacade.getElementFactory(myFixture.getProject()).createTypeFromText("java.lang.ThreadLocal<? extends String>", null)));
  }
}