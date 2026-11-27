package com.siyeh.ig.fixes.performance;

import com.intellij.codeInspection.InspectionsBundle;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.TrivialStringConcatenationInspection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class TrivialStringConcatenationFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(), "performance/trivial_string_concatenation",
                        InspectionGadgetsBundle.message("string.replace.quickfix"));

  private String fixAll;

  @BeforeEach
  void setUp() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    TrivialStringConcatenationInspection inspection = new TrivialStringConcatenationInspection();
    inspection.skipIfNecessary = false;
    if (ig.get().getTestName(true).contains("SkipIfNecessary")) {
      inspection.skipIfNecessary = true;
    }
    myFixture.enableInspections(inspection);
    fixAll = InspectionsBundle.message("fix.all.inspection.problems.in.file",
                                       InspectionGadgetsBundle.message("trivial.string.concatenation.display.name"));
  }

  @Test
  public void testParentheses() { ig.get().doTest(); }
  @Test
  public void testParentheses2() { ig.get().doTest(); }
  @Test
  public void testBinaryNull() { ig.get().doTest(); }
  @Test
  public void testAtTheEnd() { ig.get().doTest(); }
  @Test
  public void testSeveralEmptyString() {
    ig.get().doTest(fixAll);
  }
  @Test
  public void testStartComments() {
    ig.get().doTest();
  }
  @Test
  public void testMiddleComments() {
    ig.get().doTest();
  }
  @Test
  public void testEndComments() {
    ig.get().doTest();
  }

  @Test
  public void testNotSeenString() {
    ig.get().doTest(fixAll);
  }
  @Test
  public void testNotSeenString_SkipIfNecessary() {
    ig.get().doTest(fixAll);
  }
}