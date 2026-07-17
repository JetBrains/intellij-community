package com.intellij.codeInspection.tests.kotlin;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.JavaInspectionTestCase;
import com.siyeh.ig.dependency.CyclicClassDependencyInspection;

public abstract class KotlinCyclicClassDependencyInspectionTest extends JavaInspectionTestCase {
  private final CyclicClassDependencyInspection myGlobalTool = new CyclicClassDependencyInspection();

  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/jvm";
  }

  private String getGlobalTestDir() {
    return "kotlinCyclicClassDependency/" + getTestName(true);
  }

  public void testCompanionObject() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testOutsiderCompanionObject() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }

  public void testRealCycle() {
    doTest(getGlobalTestDir(), myGlobalTool);
  }
}
