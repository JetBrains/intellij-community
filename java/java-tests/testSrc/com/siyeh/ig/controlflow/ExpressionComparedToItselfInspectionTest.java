package com.siyeh.ig.controlflow;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.intellij.testFramework.LightProjectDescriptor;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ExpressionComparedToItselfInspectionTest extends LightJavaInspectionTestCase {
  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_21_ANNOTATED;
  }

  public void testExpressionComparedToItself() {
    doTest();
  }
  
  public void testExpressionComparedToItselfNoSideEffect() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    ExpressionComparedToItselfInspection inspection = new ExpressionComparedToItselfInspection();
    inspection.ignoreSideEffectConditions = getTestName(false).contains("NoSideEffect");
    return inspection;
  }
}