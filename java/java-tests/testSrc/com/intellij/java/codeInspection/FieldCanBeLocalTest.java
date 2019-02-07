// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase;

public class FieldCanBeLocalTest extends LightCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/fieldCanBeLocal";
  }

  private void doTest() {
    myFixture.enableInspections(new FieldCanBeLocalInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testFieldUsedInAnotherMethodAsQualifier() {
    doTest();
  }

  public void testFieldUsedForWritingInLambda() {
    doTest();
  }

  public void testFieldUsedInConstantInitialization() {
    doTest();
  }

  public void testLocalVar2InnerClass() {
    doTest();
  }

  public void testFieldReferencedFromAnotherObject() {
    doTest();
  }

  public void testDontSimplifyRuntimeConstants() {
    doTest();
  }

  public void testInnerClassConstructor() {
    doTest();
  }

  public void testStateField() {
    doTest();
  }

  public void testConstructorThisRef() {
    doTest();
  }

  public void testStaticQualifiedFieldAccessForWriting() {
    doTest();
  }

  public void testIgnoreAnnotated() {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testInnerClassFieldInitializer() {
    doTest();
  }

  public void testFieldWithImmutableType() {
    doTest();
  }

  public void testTwoMethodsNotIgnoreMultipleMethods () {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    myFixture.enableInspections(inspection);
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testStaticFinal() {
    doTest();
  }

  public void testTwoMethods() {
    doTest();
  }

  public void testNotConstantInitializer() {
    doTest();
  }

  public void testLocalStateVar2InnerClass() {
    doTest();
  }

  public void testSimple() {
    doTest();
  }

  public void testStaticAccess() {
    doTest();
  }

  public void testConstructor() {
    doTest();
  }
}
