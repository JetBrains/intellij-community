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
    doTest();
  }

  //inherited javadoc
  public void testMissedTags() throws Exception {
    doTest();
  }

  public void testDoubleMissedTags() throws Exception{
    doTest();
  }

  public void testMissedThrowsTag() throws Exception {
    doTest();
  }

  public void testMisorderedThrowsTag() throws Exception {
    doTest();
  }

  public void testGenericsParams() throws Exception {
    doTest();
  }

  public void testIgnoreDuplicateThrows() throws Exception {
    final JavaDocLocalInspection inspection = new JavaDocLocalInspection();
    inspection.IGNORE_DUPLICATED_THROWS = true;
    doTest("javaDocInspection/" + getTestName(true), inspection);
  }
}
