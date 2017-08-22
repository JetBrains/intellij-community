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

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantCast15Test extends InspectionTestCase {
  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(myJavaFacade.getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
    ModuleRootModificationUtil.setModuleSdk(getModule(), getTestProjectSdk());
  }

  @Override
  protected Sdk getTestProjectSdk() {
    // in jdk 8 some casts are unnecessary
    return IdeaTestUtil.getMockJdk17();
  }

  private void doTest() {
    final LocalInspectionToolWrapper toolWrapper = new LocalInspectionToolWrapper(new RedundantCastInspection());
    doTest("redundantCast/generics/" + getTestName(false), toolWrapper, "java 1.5");
  }

  public void testBoxingInRef() { doTest(); }

  public void testBoxingInConditional() { doTest(); }

  public void testInference1() { doTest(); }

  public void testInference2() { doTest(); }

  public void testInference3() { doTest(); }

  public void testNullInVarargsParameter() { doTest(); }

  public void testWrapperToPrimitiveCast() { doTest(); }

  public void testEnumConstant() { doTest(); }

  public void testRawCast() { doTest();}
  public void testCastToUnboundWildcard() { doTest();}

  public void testRawCastsToAvoidIncompatibility() { doTest();}

  public void testIDEA22899() { doTest();}
  public void testRawCast1() { doTest();}
  public void testInferenceFromCast() { doTest();}
  public void testGetClassProcessing() { doTest();}
  public void testInstanceOfChecks() { doTest();}
  public void testForEachValue() { doTest();}
  public void testForEachValueIDEA126166() { doTest();}
  public void testCaseThrowable() { doTest();}
  public void testSafeTempVarName() { doTest();}

  public void testTypeParameterAccessChecksJava7() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), getTestRootDisposable());
    doTest();
  }

  public void testBoxingTopCast() {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_7, getModule(), getTestRootDisposable());
    doTest();
  }

  public void testIgnore() {
    final RedundantCastInspection castInspection = new RedundantCastInspection();
    castInspection.IGNORE_ANNOTATED_METHODS = true;
    castInspection.IGNORE_SUSPICIOUS_METHOD_CALLS = true;
    final LocalInspectionToolWrapper tool = new LocalInspectionToolWrapper(castInspection);
    doTest("redundantCast/generics/" + getTestName(false), tool, "java 1.5");
  }
  public void testDifferentNullness() { doTest();}
}