package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.Nullable;

public class ConstantNamingConventionInspectionTest extends AbstractFieldNamingConventionInspectionTest {

  public void testConstantNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true,new ConstantNamingConvention().getShortName());
    return inspection;
  }
}