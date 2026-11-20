package com.siyeh.ig.style;

import com.intellij.testFramework.fixtures.JavaTestProject;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.junit5.AnalysisHostTest;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.ArrayUtilRt;
import com.siyeh.ig.JavaInspectionFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.JavaTestFixtures.javaTestProjectFixture;
import static com.siyeh.ig.InspectionTestFixtures.javaInspectionFixture;
import static com.siyeh.ig.LightJavaInspectionFixture.INSPECTION_GADGETS_TEST_DATA_PATH;

@AnalysisHostTest
public class FieldMayBeFinalInspectionTest {
  private static final TestFixture<? extends JavaTestProject> projectFixture = javaTestProjectFixture(LightJavaCodeInsightFixtureTestCase.JAVA_11);
  private final TestFixture<? extends JavaInspectionFixture> inspectionFixture =
    javaInspectionFixture(projectFixture, () -> new FieldMayBeFinalInspection(), ArrayUtilRt.EMPTY_STRING_ARRAY,
      INSPECTION_GADGETS_TEST_DATA_PATH + "com/siyeh/igtest/style/field_final");

  // The language server gives other warnings. LSP-2483 tracks the difference.
  @AnalysisHostTest(hosts = AnalysisHostTest.Host.IDE)
  @Test
  public void testFieldMayBeFinal() {
    inspectionFixture.get().doTest();
  }
  @Test
  public void testAnonymousObject() {
    inspectionFixture.get().doTest();
  }
}