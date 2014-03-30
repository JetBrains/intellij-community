/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.codeInspection.ex.EntryPointsManagerBase;
import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;

/**
 * @author max
 */
public class UnusedDeclarationTest extends InspectionTestCase {
  private UnusedDeclarationInspection myTool;
  private GlobalInspectionToolWrapper myToolWrapper;

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myToolWrapper = getUnusedDeclarationWrapper();
    myTool = (UnusedDeclarationInspection)myToolWrapper.getTool();
  }

  private void doTest() {
    doTest("deadCode/" + getTestName(true), myToolWrapper);
  }

  public void testSCR6067() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSingleton() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSCR9690() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testFormUsage() {
    myTool.ADD_NONJAVA_TO_ENTRIES = false;
    doTest();
  }

  public void testSerializable() {
    doTest();
  }

  public void testPackageLocal() {
    doTest();
  }

  public void testReachableFromMain() {
    myTool.ADD_MAINS_TO_ENTRIES = true;
    doTest();
  }

  public void testMutableCalls() {
    doTest();
  }

  public void testStaticMethods() {
    doTest();
  }

  public void testSuppress() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
  }

  public void testSuppress1() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
  }

  public void testSuppress2() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
  }

  public void testChainOfSuppressions() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
  }

  public void testReachableFromXml() {
    doTest();
  }

  public void testChainOfCalls() {
    doTest();
  }

  public void testReachableFromFieldInitializer() {
    doTest();
  }

  public void testReachableFromFieldArrayInitializer() {
    doTest();
  }

  public void testConstructorReachableFromFieldInitializer() {
    doTest();
  }

  public void testAdditionalAnnotations() {
    final String testAnnotation = "Annotated";
    EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.add(testAnnotation);
    try {
      doTest();
    }
    finally {
      EntryPointsManagerBase.getInstance(getProject()).ADDITIONAL_ANNOTATIONS.remove(testAnnotation);
    }
  }

  public void testAnnotationInterface() {
    LanguageLevelProjectExtension.getInstance(getJavaFacade().getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    doTest();
  }

  public void testJunitEntryPoint() {
    doTest();
  }

  public void testJunitAbstractClassWithInheritor() {
    doTest();
  }

  public void testJunitAbstractClassWithoutInheritor() {
    doTest();
  }

  public void testJunitEntryPointCustomRunWith() {
    doTest();
  }

  public void testConstructorCalls() {
    doTest();
  }

  public void testConstructorCalls1() {
    doTest();
  }

  public void testNonJavaReferences() {
    doTest();
  }

  public void testEnumInstantiation() {
    doTest();
  }

  public void testEnumValues() {
    doTest();
  }

  public void testUsagesInAnonymous() {
    doTest();
  }

  public void testAbstractClassWithSerializableSubclasses() {
    doTest();
  }

  public void testClassLiteralRef() {
    doTest();
  }
}
