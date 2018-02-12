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
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.varScopeCanBeNarrowed.FieldCanBeLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author ven
 */
public class FieldCanBeLocalTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() {
    doTest("fieldCanBeLocal/" + getTestName(true), new FieldCanBeLocalInspection());
  }

  public void testSimple () { doTest(); }

  public void testTwoMethods () { doTest(); }
  public void testTwoMethodsNotIgnoreMultipleMethods () {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    inspection.IGNORE_FIELDS_USED_IN_MULTIPLE_METHODS = false;
    doTestConfigured(inspection); 
  }

  public void testConstructor () { doTest(); }
  public void testConstructorThisRef() { doTest(); }
  public void testStaticFinal() { doTest(); }
  public void testStaticAccess() { doTest(); }
  public void testInnerClassConstructor() { doTest(); }
  public void testLocalVar2InnerClass() { doTest(); }
  public void testStateField() { doTest(); }
  public void testLocalStateVar2InnerClass() { doTest(); }
  public void testNotConstantInitializer() {doTest();}
  public void testInnerClassFieldInitializer() {doTest();}
  public void testFieldUsedInConstantInitialization() {doTest();}
  public void testFieldWithImmutableType() {doTest();}
  public void testFieldUsedForWritingInLambda() {doTest();}
  public void testStaticQualifiedFieldAccessForWriting() {doTest();}
  public void testFieldReferencedFromAnotherObject() {doTest();}
  public void testDontSimplifyRuntimeConstants() {doTest();}
  public void testIgnoreAnnotated() {
    final FieldCanBeLocalInspection inspection = new FieldCanBeLocalInspection();
    doTestConfigured(inspection);
  }

  public void testFieldUsedInAnotherMethodAsQualifier() {
    doTest();
  }

  private void doTestConfigured(FieldCanBeLocalInspection inspection) {
    inspection.EXCLUDE_ANNOS.add(Deprecated.class.getName());
    doTest("fieldCanBeLocal/" + getTestName(true), inspection);
  }
}
