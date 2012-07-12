package com.intellij.codeInspection;

import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.InspectionTestCase;

public class RedundantCastTest extends InspectionTestCase {

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  private void doTest() throws Exception {
    doTest("redundantCast/" + getTestName(false), new RedundantCastInspection());
  }

  public void testAmbigousParm1() throws Exception { doTest(); }

  public void testAmbigousParm2() throws Exception { doTest(); }

  public void testAmbigousParm3() throws Exception { doTest(); }

  public void testAmbigousParm4() throws Exception { doTest(); }

  public void testAmbigousParm5() throws Exception { doTest(); }

  public void testOneOfTwo() throws Exception { doTest(); }

  public void testAnyOfTwo() throws Exception { doTest(); }

  public void testNew1() throws Exception { doTest(); }

  public void testAssignment1() throws Exception { doTest(); }

  public void testInitializer1() throws Exception { doTest(); }

  public void testShortToShort() throws Exception { doTest(); }

  public void testVirtualMethod1() throws Exception { doTest(); }

  public void testVirtualMethod2() throws Exception { doTest(); }

  public void testVirtualMethod3() throws Exception { doTest(); }

  public void testDoubleCast1() throws Exception { doTest(); }

  public void testDoubleCast2() throws Exception { doTest(); }

  public void testDoubleCast3() throws Exception { doTest(); }

  public void testDoubleCast4() throws Exception { doTest(); }

  public void testDoubleCast5() throws Exception { doTest(); }

  public void testShortVsInt() throws Exception { doTest(); }

  public void testTruncation() throws Exception { doTest(); }

  public void testIntToDouble() throws Exception { doTest(); }

  public void testSCR6907() throws Exception { doTest(); }

  public void testSCR11555() throws Exception { doTest(); }

  public void testSCR13397() throws Exception { doTest(); }

  public void testSCR14502() throws Exception { doTest(); }

  public void testSCR14559() throws Exception { doTest(); }

  public void testSCR15236() throws Exception { doTest(); }

  public void testComparingToNull() throws Exception { doTest(); }

  public void testInaccessible() throws Exception { doTest(); }

  public void testInConditional() throws Exception { doTest(); }

  public void testDifferentFields() throws Exception { doTest(); }

  public void testNestedThings() throws Exception { doTest(); }

  public void testIDEADEV6818() throws Exception { doTest(); }

  public void testIDEADEV15170() throws Exception { doTest(); }

  public void testIDEADEV25675() throws Exception { doTest(); }
  public void testFieldAccessOnTheLeftOfAssignment() throws Exception { doTest(); }
  
  public void testNestedCast() throws Exception { doTest(); }
  public void testPrimitiveInsideSynchronized() throws Exception { doTest(); }

  public void testInConditionalPreserveResolve() throws Exception { doTest();}
  public void testArrayAccess() throws Exception { doTest();}
}
