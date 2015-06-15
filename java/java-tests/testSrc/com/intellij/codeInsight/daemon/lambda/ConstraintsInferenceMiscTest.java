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
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.testFramework.IdeaTestUtil;
import org.jetbrains.annotations.NonNls;

public class ConstraintsInferenceMiscTest extends LightDaemonAnalyzerTestCase {
  @NonNls static final String BASE_PATH = "/codeInsight/daemonCodeAnalyzer/lambda/constraints";

  public void testEqualityUnboundWildcard() throws Exception {
    doTest(false);
  }

  public void testPrimitiveTypesCompatibility() throws Exception {
    doTest(false);
  }

  public void testTypeCompatibilityUncheckedConversion() throws Exception {
    doTest(false);
  }

  public void testTypeCompatibilityUncheckedConversionReturnConstraints() throws Exception {
    doTest(false);
  }

  public void testSubtypingExtendsSuper() throws Exception {
    doTest(false);
  }

  public void testUncheckedBoundsWithErasure() throws Exception {
    doTest(false);
  }

  public void testLambdaGroundTest() throws Exception {
    doTest(false);
  }

  public void testIntersectionTypeStrictSubtypingConstraint() throws Exception {
    doTest(false);
  }

  public void testWildcardParameterizedReturnTypeConflictWithParameterTypes() throws Exception {
    doTest(false);
  }

  public void testSubtypingConstraintWithSuperCapturedWildcard() throws Exception {
    doTest(false);
  }

  private void doTest(final boolean checkWarnings) {
    doTest(BASE_PATH + "/" + getTestName(false) + ".java", checkWarnings, false);
  }

  @Override
  protected Sdk getProjectJDK() {
    return IdeaTestUtil.getMockJdk18();
  }
}
