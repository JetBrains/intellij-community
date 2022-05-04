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
    myFixture.addClass("package foo;\n" +
                       "\n" +
                       "public class Foo {\n" +
                       "\n" +
                       "    public static <T extends Private> T m0() {return null;}\n" +
                       "    public static Private m1() {return null;}\n" +
                       "    public static Private[] m2() {return null;}\n" +
                       "    public static Package m3() {return null;}\n" +
                       "    public static Protected m4() {return null;}\n" +
                       "    public static Public m5() {return null;}\n" +
                       "\n" +
                       "    private static class Private {}\n" +
                       "    static class Package {}\n" +
                       "    protected static class Protected {}\n" +
                       "    public static class Public {}\n" +
                       "}");
    doTest();
  }
}
