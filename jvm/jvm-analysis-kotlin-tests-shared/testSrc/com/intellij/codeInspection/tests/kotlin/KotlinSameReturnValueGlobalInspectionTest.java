package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.sameReturnValue.SameReturnValueInspection;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;


@TestApplication
public abstract class KotlinSameReturnValueGlobalInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  protected final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm");

  private final SameReturnValueInspection myGlobalTool = new SameReturnValueInspection();

  private String getGlobalTestDir() {
    return "sameReturnValue/" + javaInspection.get().getTestName(true);
  }

  @Test
  public void testJava() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

  @Test
  public void testKotlin() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

  @Test
  public void testMixed() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }
}
