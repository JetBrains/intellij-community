// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.idea.TestFor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

@TestDataPath("$CONTENT_ROOT/testData/inspection/redundantCast")
public class RedundantCastInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantCast";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    IdeaTestUtil.setProjectLanguageLevel(getProject(), LanguageLevel.JDK_1_3);
  }

  private void doTest() {
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testAmbiguousParameter1() { doTest(); }
  public void testAmbiguousParameter2() { doTest(); }
  public void testAmbiguousParameter3() { doTest(); }
  public void testAmbiguousParameter4() { doTest(); }
  public void testAmbiguousParameter5() { doTest(); }
  public void testConditionalNoType() { doTest(); }
  public void testOneOfTwo() { doTest(); }
  public void testAnyOfTwo() { doTest(); }
  public void testNew1() { doTest(); }
  public void testAssignment1() { doTest(); }
  public void testInitializer1() { doTest(); }
  public void testShortToShort() { doTest(); }
  public void testVirtualMethod1() { doTest(); }
  public void testVirtualMethod2() { doTest(); }
  public void testVirtualMethod3() { doTest(); }
  public void testDoubleCast1() { doTest(); }
  public void testDoubleCast2() { doTest(); }
  public void testDoubleCast3() { doTest(); }
  public void testDoubleCast4() { doTest(); }
  public void testDoubleCast5() { doTest(); }
  public void testShortVsInt() { doTest(); }
  public void testTruncation() { doTest(); }
  public void testIntToDouble() { doTest(); }
  public void testSCR6907() { doTest(); }
  public void testSCR11555() { doTest(); }
  public void testSCR13397() { doTest(); }
  public void testSCR14502() { doTest(); }
  public void testSCR14559() { doTest(); }
  public void testSCR15236() { doTest(); }
  public void testComparingToNull() { doTest(); }
  public void testInaccessible() { doTest(); }
  public void testInConditional() { doTest(); }
  public void testDifferentFields() { doTest(); }
  public void testNestedThings() { doTest(); }
  public void testIDEADEV6818() { doTest(); }
  public void testIDEADEV15170() { doTest(); }
  public void testNotRedundantBecauseOfException() { doTest(); }
  public void testIDEADEV25675() { doTest(); }
  public void testFieldAccessOnTheLeftOfAssignment() { doTest(); }
  public void testNestedCast() { doTest(); }
  public void testPrimitiveInsideSynchronized() { doTest(); }
  public void testInConditionalPreserveResolve() { doTest(); }
  public void testArrayAccess() { doTest(); }
  public void testSwitchUnboxing() { doTest(); }
  public void testVararg() { doTest(); }

  public void testPackagePrivate() {
    myFixture.addClass("package a; public class A {void foo() {}}");
    myFixture.addClass("package a.b; public class B extends a.A { public void foo(){}}");
    doTest();
  }

  @TestFor(issues = "IDEA-317124")
  public void testInstanceofPattern() { doTest(); }

  public void testSwitchSelectorJava21() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest); }
  
  public void testVarInitializer() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_21, this::doTest); }
}
