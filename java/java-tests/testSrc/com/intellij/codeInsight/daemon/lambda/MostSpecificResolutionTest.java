/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.codeInsight.daemon.lambda;

import com.intellij.codeInsight.daemon.LightDaemonAnalyzerTestCase;
import com.intellij.codeInspection.deadCode.UnusedDeclarationInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
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

  public void testVoidConflict() throws Exception {
    doTest();
  }

  public void testNestedLambdaSpecifics() throws Exception {
    doTest();
  }

  public void testBothVarargs() throws Exception {
    doTest();
  }

  public void testNestedVarargs() throws Exception {
    doTest();
  }

  public void testMostSpecificForSameFunctionalTypes() throws Exception {
    doTest();
  }

  public void testIDEA121884() throws Exception {
    doTest();
  }

  public void testIDEA121999() throws Exception {
    doTest();
  }

  public void testRelatedSAMErasures() throws Exception {
    doTest();
  }

  public void testJDK8034223() throws Exception {
    doTest();
  }

  public void testIDEA123352() throws Exception {
    doTest();
  }

  public void testIncompleteMethodInInterface() throws Exception {
    doTest(false);
  }

  public void testMostSpecificByReturnType() throws Exception {
    doTest();
  }

  public void testDifferentParamsLength() throws Exception {
    doTest(false);
  }

  public void testNoReturnTypeResolutionForThrownException() throws Exception {
    doTest(false);
  }

  public void testBoxingAndOverloadResolution() throws Exception {
    doTest();
  }

  public void testSuperMethodsInExactCheck() throws Exception {
    doTest();
  }

  public void testTargetTypeParameter() throws Exception {
    doTest(false);
  }

  public void testJDK8042508() throws Exception {
    if (Registry.is("JDK8042508.bug.fixed", false)) {
      doTest(false);
    }
  }

  public void testIDEA125855() throws Exception {
    doTest();
  }

  public void testIDEA127584() throws Exception {
    doTest();
  }

  public void testVarargsSpecificsDuringMethodReferenceResolve() throws Exception {
    doTest();
  }

  public void testFunctionalTypeComparisonWhenMethodsAreNotGeneric() throws Exception {
    doTest(false);
  }

  public void testInferSpecificForGenericMethodWhenCallProvidesExplicitTypeArguments() throws Exception {
    doTest(false);
  }

  public void testIncompatibleSiteSubstitutionBounds() throws Exception {
    doTest(false);
  }

  public void testEnsureArgTypesAreNotCalculatedDuringOverload() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(true);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
