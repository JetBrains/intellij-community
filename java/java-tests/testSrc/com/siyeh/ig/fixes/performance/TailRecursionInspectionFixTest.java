// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.performance;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.psi.codeStyle.CommonCodeStyleSettings;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.performance.TailRecursionInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class TailRecursionInspectionFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new TailRecursionInspection()), "performance/tail_recursion",
                        InspectionGadgetsBundle.message("tail.recursion.replace.quickfix"));

  @Test
  public void testCallOnOtherInstance1() { ig.get().doTest(); }
  @Test
  public void testCallOnOtherInstance2() { ig.get().doTest(); }
  @Test
  public void testDependency1() { ig.get().doTest(); }
  @Test
  public void testDependency2() { ig.get().doTest(); }
  @Test
  public void testDependency3() { ig.get().doTest(); }
  @Test
  public void testDependency4() { ig.get().doTest(); }
  @Test
  public void testThisVariable() { ig.get().doTest(); }
  @Test
  public void testUnmodifiedParameter() { ig.get().doTest(); }
  @Test
  public void testRemoveEmptyElse() { ig.get().doTest(); }
  @Test
  public void testRemoveEmptyElseCommentAtLineStart() { ig.get().doTest(); }
  @Test
  public void testVoidMethod1() { ig.get().doTest(); }
  @Test
  public void testVoidMethod2() { ig.get().doTest(); }
  @Test
  public void testAndOrChain() { ig.get().doTest(); }

  @Test
  public void testClassInOtherFile() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.addClass("""
                         class Container extends ClassInOtherFile {
                           Container(Container parent) {
                             super(parent);
                           }
                         }
                         """);
    ig.get().doTest();
  }

  @Test
  public void testAndOrChain2() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    CommonCodeStyleSettings settings = CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE);
    int oldValue = settings.IF_BRACE_FORCE;
    settings.IF_BRACE_FORCE = CommonCodeStyleSettings.FORCE_BRACES_ALWAYS;
    try {
      ig.get().doTest();
    }
    finally {
      settings.IF_BRACE_FORCE = oldValue;
    }
  }
}
