package com.intellij.codeInspection;

import com.intellij.codeInspection.ex.GlobalInspectionToolWrapper;
import com.intellij.codeInspection.ex.InspectionProfileImpl;
import com.intellij.codeInspection.ex.InspectionToolRegistrar;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantSuppressTest extends InspectionTestCase {
  private GlobalInspectionToolWrapper myWrapper;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    InspectionToolRegistrar.getInstance().ensureInitialized();
    myWrapper = new GlobalInspectionToolWrapper(new RedundantSuppressInspection());
  }

  public void testDefaultFile() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    doTest();
    InspectionProfileImpl.INIT_INSPECTIONS = false;
  }

  public void testSuppressAll() throws Exception {
    InspectionProfileImpl.INIT_INSPECTIONS = true;
    try {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = true;
      doTest();
    }
    finally {
      ((RedundantSuppressInspection)myWrapper.getTool()).IGNORE_ALL = false;
      InspectionProfileImpl.INIT_INSPECTIONS = false;
    }
  }

  private void doTest() throws Exception {
    doTest("redundantSuppress/" + getTestName(true), myWrapper,"java 1.5",true);
  }
}
