package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.JavaInspectionFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ig.dependency.CyclicClassDependencyInspection;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionFixture;
import static com.intellij.testFramework.JavaInspectionFixtureKt.javaInspectionProjectFixture;

@TestApplication
public abstract class KotlinCyclicClassDependencyInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = javaInspectionProjectFixture();
  protected final TestFixture<JavaInspectionFixture> javaInspection = javaInspectionFixture(projectFixture, JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm");

  private final CyclicClassDependencyInspection myGlobalTool = new CyclicClassDependencyInspection();

  private String getGlobalTestDir() {
    return "kotlinCyclicClassDependency/" + javaInspection.get().getTestName(true);
  }

  @Test
  public void testCompanionObject() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

  @Test
  public void testNonCompanionObject() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

  @Test
  public void testOutsiderCompanionObject() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }

  @Test
  public void testRealCycle() {
    javaInspection.get().doTest(getGlobalTestDir(), myGlobalTool);
  }
}
