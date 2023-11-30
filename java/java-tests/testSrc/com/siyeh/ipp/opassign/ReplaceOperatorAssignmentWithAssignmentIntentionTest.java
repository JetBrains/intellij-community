package com.siyeh.ipp.opassign;

import com.intellij.codeInspection.CommonQuickFixBundle;
import com.siyeh.ipp.IPPTestCase;

/**
 * @see ReplaceOperatorAssignmentWithAssignmentIntention
 */
public class ReplaceOperatorAssignmentWithAssignmentIntentionTest extends IPPTestCase {
  public void testOperatorAssignment1() { doTest(); }
  public void testDoubleOpAssign() { doTest(); }
  public void testStringOpAssign() { doTest(); }
  public void testByteOpAssign() { doTest(); }
  public void testPrecedence() { doTest(); }
  public void testPolyadicAssignment() { doTest(CommonQuickFixBundle.message("fix.replace.x.with.y", "*=", "=")); }
  public void testConditionalAssignment() { doTest(CommonQuickFixBundle.message("fix.replace.x.with.y",  "*=", "=")); }
  public void testIncomplete() { doTest(); }
  public void testCastNecessary() { doTest(); }
  public void testAssignmentAssign() { doTest(); }
  public void testAdditionAssignmentAssign() { doTest(); }
  public void testInstanceofAssign() { doTest(); }

  @Override
  protected String getIntentionName() {
    return CommonQuickFixBundle.message("fix.replace.x.with.y", "+=", "=");
  }

  @Override
  protected String getRelativePath() {
    return "opassign/assignment";
  }
}
