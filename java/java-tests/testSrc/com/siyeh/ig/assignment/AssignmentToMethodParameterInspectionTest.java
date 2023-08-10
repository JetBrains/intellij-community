package com.siyeh.ig.assignment;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;

/**
 * @author bas
 */
public class AssignmentToMethodParameterInspectionTest extends LightJavaInspectionTestCase {

  public void testAssignmentToMethodParameterMissesCompoundAssign() {
    myFixture.enableInspections(new AssignmentToMethodParameterInspection());
    doTest();
  }

  public void testIgnoreTransformationOfParameter() {
    final AssignmentToMethodParameterInspection inspection = new AssignmentToMethodParameterInspection();
    inspection.ignoreTransformationOfOriginalParameter = true;
    myFixture.enableInspections(inspection);
    doTest();
  }

  @Override
  protected InspectionProfileEntry getInspection() {
    return null;
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/assignment/method_parameter";
  }
}