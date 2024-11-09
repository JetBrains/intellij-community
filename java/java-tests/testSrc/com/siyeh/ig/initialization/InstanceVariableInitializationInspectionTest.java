// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.initialization;

import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;

public class InstanceVariableInitializationInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return LightJavaInspectionTestCase.INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/initialization/field";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_8;
  }

  private void doTest() {
    myFixture.addClass("""
                         package junit.framework;
                         public class TestCase {
                           protected void setUp() throws Exception {}
                         }
                         """);
    myFixture.enableInspections(new InstanceVariableInitializationInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testInstanceVariableInitialization() {
    doTest();
  }
}
