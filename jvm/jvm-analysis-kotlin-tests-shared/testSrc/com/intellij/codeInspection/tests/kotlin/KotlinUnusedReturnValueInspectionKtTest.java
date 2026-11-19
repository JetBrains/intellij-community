package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.unusedReturnValue.UnusedReturnValue;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;


@TestApplication
public abstract class KotlinUnusedReturnValueInspectionKtTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  protected final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm");

  private final UnusedReturnValue myGlobalTool = new UnusedReturnValue();

  private String getGlobalTestDir() {
    return "unusedReturnValue/" + javaInspection.get().getTestName(true);
  }

  @Test
  public void testUsedPropertyBySimpleName() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

}
