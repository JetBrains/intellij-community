// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.LightJavaInspectionFixture;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;
import static com.siyeh.ig.LightJavaInspectionFixtureKt.lightJavaInspectionFixture;

@TestApplication
public class IfCanBeSwitchInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaInspectionFixture> inspectionFixture =
    lightJavaInspectionFixture(projectFixture, () -> getInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/migration/if_switch");

  @Test
  public void testIfCanBeSwitch() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testPatternIfCanBeSwitch() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testSeveralIfStatements() {
    inspectionFixture.get().doTest();
  }

  @Test
  public void testNullUnsafe(){ doUnsafeNullTest(); }

  @Test
  public void testSeveralUnconditionalCases() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doAllTest();
    });
  }

  @Test
  public void testSeveralUnconditionalCasesWithPrimitives() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    IdeaTestUtil.withLevel(myFixture.getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doAllTest();
    });
  }

  private void doAllTest() {
    JavaCodeInsightTestFixture myFixture = inspectionFixture.get().getFixture();
    String name = inspectionFixture.get().getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, true, true);
  }

  @NotNull IfCanBeSwitchInspection myInspection = new IfCanBeSwitchInspection();

  @Nullable
  private InspectionProfileEntry getInspection() {
    myInspection.suggestIntSwitches = true;
    myInspection.suggestEnumSwitches = true;
    myInspection.minimumBranches = 2;
    myInspection.setOnlySuggestNullSafe(true);
    return myInspection;
  }

  private void doUnsafeNullTest(){
    boolean suggestOnlyNullSafe = myInspection.suggestEnumSwitches;
    try {
      myInspection.setOnlySuggestNullSafe(false);
      inspectionFixture.get().doTest();
    } finally {
      myInspection.setOnlySuggestNullSafe(suggestOnlyNullSafe);
    }
  }
}
