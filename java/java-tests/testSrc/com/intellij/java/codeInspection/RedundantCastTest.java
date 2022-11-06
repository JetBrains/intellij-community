// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public class RedundantCastTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/inspection/redundantCast";
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_3);
  }

  private void doTest() {
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.testHighlighting(getTestName(false) + ".java");
  }

  public void testAmbigousParm1() { doTest(); }

  public void testAmbigousParm2() { doTest(); }

  public void testAmbigousParm3() { doTest(); }

  public void testAmbigousParm4() { doTest(); }

  public void testAmbigousParm5() { doTest(); }

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

  public void testIDEADEV25675() { doTest(); }
  public void testFieldAccessOnTheLeftOfAssignment() { doTest(); }
  
  public void testNestedCast() { doTest(); }
  public void testPrimitiveInsideSynchronized() { doTest(); }

  public void testInConditionalPreserveResolve() { doTest();}
  public void testArrayAccess() { doTest();}

  public void testPackagePrivate() {
    myFixture.addClass("package a; public class A {void foo() {}}");
    myFixture.addClass("package a.b; public class B extends a.A { public void foo(){}}");
    doTest();
  }

  public void testSwitchSelectorJava17() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest); }

  public void testSwitchSelectorJava19() { IdeaTestUtil.withLevel(getModule(), LanguageLevel.JDK_19_PREVIEW, this::doTest); }
}
