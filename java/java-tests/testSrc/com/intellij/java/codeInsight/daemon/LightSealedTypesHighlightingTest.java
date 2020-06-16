// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.codeInsight.daemon;

import com.intellij.JavaTestUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.NotNull;

public class LightSealedTypesHighlightingTest extends LightJavaCodeInsightFixtureTestCase {
  @Override
  protected String getBasePath() {
    return JavaTestUtil.getRelativeJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/advHighlightingSealedTypes";
  }

  @NotNull
  @Override
  protected LightProjectDescriptor getProjectDescriptor() {
    return JAVA_15;
  }

  public void testSealedTypesBasics() { doTest(); }
  public void testSealedFunctionalInterface() { doTest(); }
  public void testSealedRestrictedTypes() { doTest(); }
  public void testPermitsList() {
    myFixture.addClass("package p1; public class P1 extends p.AnotherPackage {}");
    myFixture.addClass("package p; public class P {}");
    doTest(); 
  }
  
  private void doTest() {
    myFixture.configureByFile(getTestName(false) + ".java");
    myFixture.checkHighlighting();
  }
}