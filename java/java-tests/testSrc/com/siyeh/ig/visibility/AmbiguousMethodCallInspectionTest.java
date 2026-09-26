package com.siyeh.ig.visibility;

import com.intellij.codeInspection.InspectionProfileEntry;
import com.siyeh.ig.LightJavaInspectionTestCase;
import org.jetbrains.annotations.Nullable;

public class AmbiguousMethodCallInspectionTest extends LightJavaInspectionTestCase {

  public void testAmbiguousMethodCall() { doTest(); }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new AmbiguousMethodCallInspection();
  }

  @Override
  protected String getBasePath() {
    return "/java/java-tests/testData/ig/com/siyeh/igtest/visibility/ambiguous";
  }
}