package com.siyeh.ig.abstraction;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

public class MagicNumberInspectionTest extends LightJavaInspectionTestCase {

  public void testMagicNumber() {
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    final MagicNumberInspection tool = new MagicNumberInspection();
    tool.ignoreInHashCode = true;
    tool.ignoreInAnnotations = true;
    tool.ignoreInitialCapacity = true;
    return tool;
  }
}