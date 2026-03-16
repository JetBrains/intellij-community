// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInspection;

import com.intellij.JavaTestUtil;
import com.intellij.codeInspection.IncorrectLazyConstantUsageInspection;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class IncorrectLazyConstantUsageInspectionTest extends LightJavaCodeInsightFixtureTestCase {

  public void testIncorrectLazyConstantUsage() {
    doTest();
  }

  public void testIncorrectLazyConstantUsageFix() {
    String name = getTestName(false);
    myFixture.configureByFile(name + ".java");
    myFixture.launchAction(myFixture.findSingleIntention("Make 'f' 'final'"));
    myFixture.checkResultByFile(name + "_after.java");
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myFixture.addClass("""
                         package java.lang;
                         import java.util.function.Supplier;
                         
                         public final class LazyConstant<V> {
                             public static <V> LazyConstant<V> of(Supplier<? extends V> supplier) {
                                 return null;
                             }
                         
                             T get();
                         }
                         """);
    myFixture.enableInspections(new IncorrectLazyConstantUsageInspection());
  }

  private void doTest() {
    myFixture.testHighlighting(true, false, false, getTestName(false) + ".java");
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_26;
  }

  @NotNull
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/inspection/incorrectLazyConstantUsage";
  }
}
