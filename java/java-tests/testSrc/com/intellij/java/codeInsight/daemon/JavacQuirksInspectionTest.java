// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.compiler.JavacQuirksInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class JavacQuirksInspectionTest extends LightJavaCodeInsightFixtureTestCase {
  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.enableInspections(new JavacQuirksInspection());
    myFixture.checkHighlighting();
  }

  @Override
  protected @NotNull LightProjectDescriptor getProjectDescriptor() {
    return JAVA_17;
  }

  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/javacQuirks";
  }

  public void testMethodReferenceRefersInaccessibleType() {
    myFixture.addClass("""
                         package foo;

                         public class Foo {

                             public static <T extends Private> T m0() {return null;}
                             public static Private m1() {return null;}
                             public static Private[] m2() {return null;}
                             public static Package m3() {return null;}
                             public static Protected m4() {return null;}
                             public static Public m5() {return null;}

                             private static class Private {}
                             static class Package {}
                             protected static class Protected {}
                             public static class Public {}
                         }""");
    doTest();
  }
}
