package com.siyeh.ig.naming;

import com.intellij.codeInspection.InspectionProfileEntry;
import org.jetbrains.annotations.Nullable;

public class EnumeratedConstantNamingConventionInspectionTest extends AbstractFieldNamingConventionInspectionTest {

  public void testEnumeratedConstantNamingConvention() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    FieldNamingConventionInspection inspection = new FieldNamingConventionInspection();
    inspection.setEnabled(true, new EnumeratedConstantNamingConvention().getShortName());
    return inspection;
  }
}