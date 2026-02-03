package com.siyeh.ig.cloneable;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class CloneDeclaresCloneNotSupportedInspectionTest extends LightJavaInspectionTestCase {

  public void testCloneDeclaresCloneNonSupportedException() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new CloneDeclaresCloneNotSupportedInspection();
  }
}