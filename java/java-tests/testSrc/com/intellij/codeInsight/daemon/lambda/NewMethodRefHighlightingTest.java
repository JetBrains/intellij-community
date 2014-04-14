/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.uncheckedWarnings.UncheckedWarningLocalInspection;
import com.intellij.codeInspection.unusedSymbol.UnusedSymbolLocalInspection;
import com.intellij.openapi.projectRoots.JavaSdkVersion;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class NewMethodRefHighlightingTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/newMethodRef";

  @NotNull
  @Override
  protected LocalInspectionTool[] configureLocalInspectionTools() {
    return new LocalInspectionTool[]{
      new UnusedSymbolLocalInspection(),
      new UncheckedWarningLocalInspection()
    };
  }

  public void testIDEA93587() throws Exception {
    doTest(true);
  }
  
  public void testIDEA106522() throws Exception {
    doTest();
  }
  
  public void testIDEA112574() throws Exception {
    doTest();
  }
  
  public void testIDEA113558() throws Exception {
    doTest(true);
  }

  public void testAfterDistinctOps() throws Exception {
    doTest(true);
  }

  public void testWildcardReturns() throws Exception {
    doTest(false);
  }

  public void testInexactMethodReferencePrimitiveBound() throws Exception {
    doTest(false);
  }

  public void testAfterCollectors1() throws Exception {
    doTest(false);
  }

  public void testAfterCollectors2() throws Exception {
    doTest(false);
  }

  public void testIDEA116359() throws Exception {
    doTest(false);
  }

  public void testAfterSliceOps() throws Exception {
    doTest(false);
  }

  public void testAssertNumberOfParameters() throws Exception {
    doTest(false);
  }

  public void testGenericArrayCreation() throws Exception {
    doTest(true);
  }

  public void testWildcardsInClassTypeQualifier() throws Exception {
    doTest();
  }

  public void testRawConstructorReferenceWithTypeParams() throws Exception {
    doTest();
  }

  public void testCheckReturnTypeForExactRefs() throws Exception {
    doTest();
  }

  public void testPotentialApplicability() throws Exception {
    doTest();
  }

  public void testLiftMethodReferenceTypeParams() throws Exception {
    doTest();
  }

  public void testHighlightReferenceWhenContradictBoundsAreInferred() throws Exception {
    doTest();
  }

  public void testAfterBounds() throws Exception {
    doTest();
  }

  public void testIncludeContainingClassParamsInResolveSetForConstructorRefs() throws Exception {
    doTest();
  }

  public void testContainingClassTypeParamsForBounds() throws Exception {
    doTest();
  }

  public void testCapturingReturnValue() throws Exception {
    doTest();
  }

  public void testIDEA117311() throws Exception {
    doTest();
  }

  public void testDeepWildcardEliminating() throws Exception {
    doTest();
  }

  public void _testLambdaExercise() throws Exception {
    doTest();
  }

  public void testIDEA118437() throws Exception {
    doTest();
  }

  public void testIDEA113297() throws Exception {
    doTest();
  }

  public void testIDEA120376() throws Exception {
    doTest();
  }

  public void testIDEA120370() throws Exception {
    doTest();
  }

  public void testExcludeContainingClassTypeParamsFromInference() throws Exception {
    doTest();
  }

  public void testEnsureTypeParametersAreNotModifiedDuringGroundTypeEvaluation() throws Exception {
    doTest();
  }

  public void testIncorrectArrayCreationSignature() throws Exception {
    doTest();
  }

  public void testRawTargetType() throws Exception {
    doTest();
  }

  public void testReturnTypeCheckForRawReceiver() throws Exception {
    doTest();
  }

  public void testStaticNonStaticReferenceTypeAmbiguity() throws Exception {
    doTest();
  }

  public void testSuperClassPotentiallyApplicableMembers() throws Exception {
    doTest();
  }

  public void testExactMethodReferencePertinentToApplicabilityCheck() throws Exception {
    doTest();
  }

  public void testAmbiguityVarargs() throws Exception {
    doTest();
  }

  public void testRawInnerClassQualifier() throws Exception {
    doTest();
  }

  public void testIDEA122100() throws Exception {
    doTest();
  }

  public void testIDEA122509() throws Exception {
    doTest();
  }

  public void testIDEA122681() throws Exception {
    doTest();
  }

  public void testIDEA112191() throws Exception {
    doTest();
  }

  public void testIDEA122018comment() throws Exception {
    doTest();
  }

  public void testIDEA123223() throws Exception {
    doTest();
  }

  public void testIDEA123248() throws Exception {
    doTest();
  }

  public void testIDEA123366() throws Exception {
    doTest();
  }

  public void testIDEA123366comment() throws Exception {
    doTest();
  }

  public void testFromReferenceWithTypeArgs() throws Exception {
    doTest();
  }

  private void doTest() {
    doTest(false);
  }

  private void doTest(boolean warnings) {
    IdeaTestUtil.setTestVersion(JavaSdkVersion.JDK_1_8, getModule(), getTestRootDisposable());
    doTestNewInference(BASE_PATH + "/" + getTestName(false) + ".java", warnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
