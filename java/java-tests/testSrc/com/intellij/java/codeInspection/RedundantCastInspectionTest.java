// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.redundantCast.RedundantCastInspection;
import com.intellij.idea.TestFor;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.testFramework.IdeaTestUtil;
import com.intellij.testFramework.TestDataPath;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;

@TestDataPath("$CONTENT_ROOT/testData/inspection/redundantCast")
@TestApplication
public class RedundantCastInspectionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(
    projectFixture, name -> name.equals("MethodSinceNewerJdk") ? LightJavaCodeInsightFixtureTestCase.JAVA_25 : LightJavaCodeInsightFixtureTestCase.JAVA_LATEST);

  @BeforeEach
  void setUp() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    codeInsightFixture.get().setTestDataPath(JavaTestUtil.getJavaTestDataPath() + "/inspection/redundantCast");
    IdeaTestUtil.setProjectLanguageLevel(myFixture.getProject(), LanguageLevel.JDK_1_3);
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.enableInspections(new RedundantCastInspection());
    myFixture.testHighlighting(myFixture.getTestName(false) + ".java");
  }

  @Test
  public void testAmbiguousParameter1() { doTest(); }
  @Test
  public void testAmbiguousParameter2() { doTest(); }
  @Test
  public void testAmbiguousParameter3() { doTest(); }
  @Test
  public void testAmbiguousParameter4() { doTest(); }
  @Test
  public void testAmbiguousParameter5() { doTest(); }
  @Test
  public void testConditionalNoType() { doTest(); }
  @Test
  public void testOneOfTwo() { doTest(); }
  @Test
  public void testAnyOfTwo() { doTest(); }
  @Test
  public void testNew1() { doTest(); }
  @Test
  public void testAssignment1() { doTest(); }
  @Test
  public void testInitializer1() { doTest(); }
  @Test
  public void testShortToShort() { doTest(); }
  @Test
  public void testVirtualMethod1() { doTest(); }
  @Test
  public void testVirtualMethod2() { doTest(); }
  @Test
  public void testVirtualMethod3() { doTest(); }
  @Test
  public void testDoubleCast1() { doTest(); }
  @Test
  public void testDoubleCast2() { doTest(); }
  @Test
  public void testDoubleCast3() { doTest(); }
  @Test
  public void testDoubleCast4() { doTest(); }
  @Test
  public void testDoubleCast5() { doTest(); }
  @Test
  public void testShortVsInt() { doTest(); }
  @Test
  public void testTruncation() { doTest(); }
  @Test
  public void testIntToDouble() { doTest(); }
  @Test
  public void testSCR6907() { doTest(); }
  @Test
  public void testSCR11555() { doTest(); }
  @Test
  public void testSCR13397() { doTest(); }
  @Test
  public void testSCR14502() { doTest(); }
  @Test
  public void testSCR14559() { doTest(); }
  @Test
  public void testSCR15236() { doTest(); }
  @Test
  public void testComparingToNull() { doTest(); }
  @Test
  public void testInaccessible() { doTest(); }
  @Test
  public void testInConditional() { doTest(); }
  @Test
  public void testDifferentFields() { doTest(); }
  @Test
  public void testNestedThings() { doTest(); }
  @Test
  public void testIDEADEV6818() { doTest(); }
  @Test
  public void testIDEADEV15170() { doTest(); }
  @Test
  public void testNotRedundantBecauseOfException() { doTest(); }
  @Test
  public void testIDEADEV25675() { doTest(); }
  @Test
  public void testFieldAccessOnTheLeftOfAssignment() { doTest(); }
  @Test
  public void testNestedCast() { doTest(); }
  @Test
  public void testPrimitiveInsideSynchronized() { doTest(); }
  @Test
  public void testInConditionalPreserveResolve() { doTest(); }
  @Test
  public void testArrayAccess() { doTest(); }
  @Test
  public void testSwitchUnboxing() { doTest(); }
  @Test
  public void testVararg() { doTest(); }

  @Test
  public void testPackagePrivate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.addClass("package a; public class A {void foo() {}}");
    myFixture.addClass("package a.b; public class B extends a.A { public void foo(){}}");
    doTest();
  }

  @Test
  @TestFor(issues = "IDEA-317124")
  public void testInstanceofPattern() { doTest(); }

  @Test
  public void testSwitchSelectorJava21() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, this::doTest); }
  
  @Test
  public void testVarInitializer() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get(); IdeaTestUtil.withLevel(myFixture.getModule(), LanguageLevel.JDK_21, this::doTest); }
  
  @Test
  @TestFor(issues = "IDEA-370995")
  public void testNoRedundantCastOnGenericFieldAccess() {doTest();}

  @Test
  @TestFor(issues = "IDEA-372049")
  public void testNestedCastInVarargs() {doTest();}

  @Test
  @TestFor(issues = "IDEA-370768")
  public void testFieldQualifierGenericSuperType() {doTest();}

  @Test
  @TestFor(issues = "IDEA-395838")
  public void testMethodSinceNewerJdk() {
    IdeaTestUtil.withLevel(codeInsightFixture.get().getModule(), LanguageLevel.JDK_21, this::doTest);
  }
}
