package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.LocalInspectionToolWrapper;
import com.intellij.codeInspection.javaDoc.JavaDocLocalInspection;

/**
 * Created by IntelliJ IDEA.
 * User: yole
 * Date: 22.09.2005
 * Time: 20:24:19
 * To change this template use File | Settings | File Templates.
 */
public class JavaDocInspectionTest extends InspectionTestCase {
  private LocalInspectionToolWrapper myWrapper;
  protected void setUp() throws Exception {
    super.setUp();
    myWrapper = new LocalInspectionToolWrapper(new JavaDocLocalInspection());
    myWrapper.initialize(getManager());
  }

  private void doTest() throws Exception {
    doTest("javaDocInspection/" + getTestName(false), myWrapper);
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
}
