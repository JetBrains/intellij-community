package com.siyeh.ig.threading;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class SynchronizationOnLocalVariableOrMethodParameterInspectionTest extends LightJavaInspectionTestCase {

  public void testSynchronizationOnLocalVariableOrMethodParameter() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new SynchronizationOnLocalVariableOrMethodParameterInspection();
  }
}
