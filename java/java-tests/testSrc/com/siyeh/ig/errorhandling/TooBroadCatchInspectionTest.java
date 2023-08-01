package com.siyeh.ig.errorhandling;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class TooBroadCatchInspectionTest extends LightJavaInspectionTestCase {

  public void testTooBroadCatchBlock() {
    doTest();
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new TooBroadCatchInspection();
  }

  @Override
  protected String getBasePath() {
    return "/plugins/InspectionGadgets/test/com/siyeh/igtest/errorhandling/toobroadcatch";
  }
}