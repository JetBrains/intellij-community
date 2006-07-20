package com.intellij.codeInspection;

import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;
import com.intellij.testFramework.InspectionTestCase;

public class JavaDocInspectionTest extends InspectionTestCase {
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
}
