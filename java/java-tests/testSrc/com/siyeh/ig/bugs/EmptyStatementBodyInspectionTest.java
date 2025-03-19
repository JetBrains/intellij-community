package com.siyeh.ig.bugs;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class EmptyStatementBodyInspectionTest extends LightJavaInspectionTestCase {

  public void testEmptyStatementBody() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    final EmptyStatementBodyInspection inspection = new EmptyStatementBodyInspection();
    inspection.m_reportEmptyBlocks = true;
    inspection.commentsAreContent = true;
    return inspection;
  }
}