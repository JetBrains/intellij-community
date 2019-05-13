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
package com.intellij.java.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class MostSpecificResolutionTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/mostSpecific";

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    enableInspectionTool(new UnusedDeclarationInspection());
  }

  public void testVoidConflict() {
    doTest();
  }

  public void testNestedLambdaSpecifics() {
    doTest();
  }

  public void testBothVarargs() {
    doTest();
  }

  public void testNestedVarargs() {
    doTest();
  }

  public void testMostSpecificForSameFunctionalTypes() {
    doTest();
  }

  public void testIDEA121884() {
    doTest();
  }

  public void testIDEA121999() {
    doTest();
  }

  public void testRelatedSAMErasures() {
    doTest();
  }

  public void testJDK8034223() {
    doTest();
  }

  public void testIDEA123352() {
    doTest();
  }

  public void testIncompleteMethodInInterface() {
    doTest(false);
  }

  public void testMostSpecificByReturnType() {
    doTest();
  }

  public void testDifferentParamsLength() {
    doTest(false);
  }

  public void testNoReturnTypeResolutionForThrownException() {
    doTest(false);
  }

  public void testBoxingAndOverloadResolution() {
    doTest();
  }

  public void testSuperMethodsInExactCheck() {
    doTest();
  }

  public void testTargetTypeParameter() {
    doTest(false);
  }

  public void testJDK8042508() {
    if (Registry.is("JDK8042508.bug.fixed", false)) {
      doTest(false);
    }
  }

  public void testIDEA125855() {
    doTest();
  }

  public void testIDEA127584() {
    doTest();
  }

  public void testVarargsSpecificsDuringMethodReferenceResolve() {
    doTest();
  }

  public void testFunctionalTypeComparisonWhenMethodsAreNotGeneric() {
    doTest(false);
  }

  public void testInferSpecificForGenericMethodWhenCallProvidesExplicitTypeArguments() {
    doTest(false);
  }

  public void testIncompatibleSiteSubstitutionBounds() {
    doTest(false);
  }

  public void testEnsureArgTypesAreNotCalculatedDuringOverload() {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }
}
