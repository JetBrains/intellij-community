// Copyright 2000-2024 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.siyeh.ig.migration;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.pom.java.JavaFeature;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IfCanBeSwitchInspectionTest extends LightJavaInspectionTestCase {

  public void testIfCanBeSwitch() {
    doTest();
  }

  public void testPatternIfCanBeSwitch() {
    doTest();
  }

  public void testSeveralIfStatements() {
    doTest();
  }

  public void testNullUnsafe(){ doUnsafeNullTest(); }

  public void testSeveralUnconditionalCases() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doAllTest();
    });
  }

  public void testSeveralUnconditionalCasesWithPrimitives() {
    IdeaTestUtil.withLevel(getModule(), JavaFeature.PRIMITIVE_TYPES_IN_PATTERNS.getMinimumLevel(), () -> {
      doAllTest();
    });
  }

  private void doAllTest() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.testHighlighting(true, true, true);
  }

  @NotNull IfCanBeSwitchInspection myInspection = new IfCanBeSwitchInspection();

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21;
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
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
      doTest();
    } finally {
      myInspection.setOnlySuggestNullSafe(suggestOnlyNullSafe);
    }
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/migration/if_switch";
  }
}
