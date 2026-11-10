// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.redundancy;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestApplication
public class RedundantJavaTimeOperationInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_21);
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/ig/com/siyeh/igtest/redundancy/redundant_java_time_operations/");
    myFixture.enableInspections(new RedundantJavaTimeOperationsInspection());
  }

  @Test
  public void testCreationPreview() {
    doTest("Remove 'LocalTime.from()' call");
  }

  @Test
  public void testRedundantCreationOfDateTime() {
    doTest(null);
  }

  @Test
  public void testChronoConverterPreview() {
    doTest("Replace with 'getNano()' call");
  }

  @Test
  public void testOffsetDateTimeConvert() {
    doTest(null);
  }

  @Test
  public void testCompareTo() {
    doTest(null);
  }

  private void doTest(@Nullable String quickFixName) {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByFile(myFixture.getTestDataPath() + "before" + myFixture.getTestName(false) + ".java");
    myFixture.testHighlighting(true, false, true);
    EdtTestUtil.runInEdtAndWait(() -> {
      if (quickFixName != null) {
        myFixture.checkPreviewAndLaunchAction(myFixture.findSingleIntention(quickFixName));
      }
      else {
        myFixture.launchAction(myFixture.findSingleIntention(InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                                                                       InspectionGadgetsBundle.message(
                                                                                         "inspection.redundant.java.time.operation.display.name"))));
      }
      myFixture.checkResultByFile("after" + myFixture.getTestName(false) + ".java");
    });
  }
}