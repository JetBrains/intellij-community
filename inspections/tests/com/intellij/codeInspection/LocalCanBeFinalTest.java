package com.intellij.codeInspection;

import com.intellij.codeInspection.localCanBeFinal.LocalCanBeFinal;
import com.intellij.testFramework.InspectionTestCase;


/**
 * @author max
 */
public class LocalCanBeFinalTest extends InspectionTestCase {
  private LocalCanBeFinal myTool;

  protected void setUp() throws Exception {
    super.setUp();
    myTool = new LocalCanBeFinal();
  }

  private void doTest() throws Exception {
    doTest("localCanBeFinal/" + getTestName(false), myTool);
  }

  public void testmultiWriteNoRead() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testif() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testincompleteAssignment() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }

  public void testparameters() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_1() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_2() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_3() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_4() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_5() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR6744_6() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7601() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428_1() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR7428() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testSCR11757() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
  public void testforeach() throws Exception {
    myTool.REPORT_PARAMETERS = true;
    myTool.REPORT_VARIABLES = true;
    doTest();
  }
}
