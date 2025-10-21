// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.java.codeInsight.intention;

import com.intellij.JavaTestUtil;
import com.intellij.java.JavaBundle;
import com.intellij.testFramework.fixtures.CodeInsightTestUtil;
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase;

public class ExpandStaticImportActionHeavyTest extends JavaCodeInsightFixtureTestCase {
  @Override
  protected String getTestDataPath() {
    return JavaTestUtil.getJavaTestDataPath() + "/codeInsight/daemonCodeAnalyzer/quickFix/expandStaticImportHeavy";
  }

  public void testPackageInfo() {
    myFixture.addClass("""
                         package a;
                         
                         public interface Interfaces {
                             @interface SomeInterface {
                         
                             }
                         }""");
    final String name = "package-info";
    CodeInsightTestUtil.doIntentionTest(myFixture, JavaBundle.message("intention.text.replace.static.import.with.qualified.access.to.0", "Interfaces"), name + ".java", name + "_after.java");
  }
}
