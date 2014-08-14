package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

public class JavaDocInspectionTest extends InspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection";
  }

  private void doTest() throws Exception {
    doTest("javaDocInspection/" + getTestName(true),  new JavaDocLocalInspection());
  }

  public void testDuplicateParam() throws Exception {
    doTest();
  }

  public void testDuplicateReturn() throws Exception {
    doTest();
  }

  // tests for duplicate class tags
  public void testDuplicateDeprecated() throws Exception {
    doTest();
  }

  // tests for duplicate field tags
  public void testDuplicateSerial() throws Exception {
    doTest();
  }

  public void testDuplicateThrows() throws Exception {
    JavaDocLocalInspection tool = new JavaDocLocalInspection();
    tool.setIgnoreDuplicatedThrows(false);
    doTest("javaDocInspection/" + getTestName(true), tool);
  }

  //inherited javadoc
  public void testMissedTags() throws Exception {
    doTest();
  }

  public void testDoubleMissedTags() throws Exception{
    doTest();
  }

  public void testMissedThrowsTag() throws Exception {
    final JavaDocLocalInspection localInspection = new JavaDocLocalInspection();
    localInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    doTest("javaDocInspection/" + getTestName(true), localInspection);
  }

  public void testMisorderedThrowsTag() throws Exception {
    doTest();
  }

  public void testGenericsParams() throws Exception {
    doTest();
  }

  public void testEnumConstructor() throws Exception {
    final JavaDocLocalInspection localInspection = new JavaDocLocalInspection();
    localInspection.METHOD_OPTIONS.ACCESS_JAVADOC_REQUIRED_FOR = "package";
    doTest("javaDocInspection/" + getTestName(true), localInspection);
  }

  public void testIgnoreDuplicateThrows() throws Exception {
    final JavaDocLocalInspection inspection = new JavaDocLocalInspection();
    doTest("javaDocInspection/" + getTestName(true), inspection);
  }

  public void testIgnoreAccessors() throws Exception {
    final JavaDocLocalInspection inspection = new JavaDocLocalInspection();
    inspection.setIgnoreSimpleAccessors(true);
    doTest("javaDocInspection/" + getTestName(true), inspection);
  }

  public void testPackageInfo() throws Exception {
    final JavaDocLocalInspection inspection = new JavaDocLocalInspection();
    inspection.IGNORE_DEPRECATED = true;
    inspection.setPackageOption("public", "@author");
    doTest("javaDocInspection/" + getTestName(true), inspection);
  }
}
