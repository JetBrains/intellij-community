package com.intellij.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.miscGenerics.RedundantArrayForVarargsCallInspection;
import com.siyeh.ig.LightInspectionTestCase;
import org.jetbrains.annotations.Nullable;

/**
 * @author cdr
 */
public class RedundantArrayForVarargsCallInspectionTest extends LightInspectionTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantArrayForVarargs/";
  }

  @Nullable
  @Override
  protected InspectionProfileEntry getInspection() {
    return new RedundantArrayForVarargsCallInspection();
  }

  public void testIDEADEV15215() throws Exception { doTest(); }
  public void testIDEADEV25923() throws Exception { doTest(); }
  public void testNestedArray() throws Exception { doTest(); }
  public void testCheckEnumConstant() throws Exception { doTest(); }
  public void testGeneric() throws Exception { doTest(); }
  public void testRawArray() throws Exception { doTest(); }
  public void testPolymorphicSignature() throws Exception { doTest(); }
}
