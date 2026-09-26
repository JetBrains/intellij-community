/*
 * Copyright 2000-2023 JetBrains s.r.o.
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
package com.siyeh.ig.fixes.junit;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.MavenDependencyUtil;
import com.siyeh.ig.LightJavaInspectionTestCase;
import com.siyeh.ig.testFrameworks.SimplifiableAssertionInspection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Bas Leijdekkers
 */
public class SimplifiableJUnitAssertionFixTest extends LightJavaInspectionTestCase {

  public void testJUnit3TestCase() { doQuickFixTest(); }

  public void testJUnit4TestCase() { doQuickFixTest(); }

  public void testIntegerPrimitive() { doQuickFixTest(); }

  public void testBoxedComparisonToEquals() { doQuickFixTest(); }

  public void testBoxedComparisonToEquals1() { doQuickFixTest(); }

  public void testDoublePrimitive() { doQuickFixTest(); }

  public void testEqualsToTrueJUnit5() { doQuickFixTest(); }

  public void testTrueToEqualsJUnit5() { doQuickFixTest(); }

  public void testTrueToEqualsJUnit5Order() { doQuickFixTest(); }

  public void testTrueToEqualsBetweenIncompatibleTypes() { doQuickFixTest(); }

  public void testFalseToNotEqualsJUnit4() { doQuickFixTest(); }

  public void testObjectEqualsToEquals() { doQuickFixTest(); }

  public void testTrueToArrayEquals() { doQuickFixTest(); }

  public void testTrueToArrayEqualsJUnit5() { doQuickFixTest(); }

  public void testNegatedTrue() { doQuickFixTest(); }

  public void testSimplifiableInstanceOf() { doQuickFixTest(); }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return LightJavaCodeInsightFixtureTestCase.JAVA_18;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/ig/com/siyeh/igfixes/junit/simplifiable_junit_assertion";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    ModuleRootModificationUtil.updateModel(getModule(), model -> {
      MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-api:5.8.1");
      MavenDependencyUtil.addFromMaven(model, "org.junit.jupiter:junit-jupiter-params:5.8.1");
      MavenDependencyUtil.addFromMaven(model, "junit:junit:4.13.2");
      MavenDependencyUtil.addFromMaven(model, "org.testng:testng:7.8.0");
    });
  }

  @Override
  protected @Nullable InspectionProfileEntry getInspection() {
    return new SimplifiableAssertionInspection();
  }

  protected void doQuickFixTest() {
    doTest();
    checkQuickFixAll();
  }
}