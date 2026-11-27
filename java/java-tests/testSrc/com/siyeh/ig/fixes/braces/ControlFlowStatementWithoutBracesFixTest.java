// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.braces;

import com.intellij.application.options.CodeStyle;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.ControlFlowStatementWithoutBracesInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;

@TestApplication
public class ControlFlowStatementWithoutBracesFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new ControlFlowStatementWithoutBracesInspection()), "statement_without_braces");

  @Test
  public void testSimpleIfBody() { doTest("if"); }
  @Test
  public void testSimpleIfExpression() { doTest("if"); }
  @Test
  public void testSimpleIfKeyword() { doTest("if"); }

  @Test
  public void testFullIfBody() { doTest("if"); }
  @Test
  public void testFullIfKeyword() { doTest("if"); }
  @Test
  public void testFullIfElseBody() { doTest("else"); }
  @Test
  public void testFullIfElseKeyword() { doTest("else"); }
  @Test
  public void testFullIfMiddle() { ig.get().assertQuickfixNotAvailable(getMessagePrefix()); }

  @Test
  public void testDoBody() { doTest("do"); }
  @Test
  public void testDoExpression() { doTest("do"); }
  @Test
  public void testDoMiddle() { doTest("do"); }

  @Test
  public void testForEachBody() { doTest("for"); }
  @Test
  public void testForEachExpression() { doTest("for"); }
  @Test
  public void testForEachKeyword() { doTest("for"); }
  @Test
  public void testForIndex() { doTest("for"); }

  @Test
  public void testWhile() { doTest("while"); }
  @Test
  public void testWhileOutside() { ig.get().assertQuickfixNotAvailable(getMessagePrefix()); }

  @Test
  public void testElseWithForStatementBody() { doTest("else"); }
  @Test
  public void testLadderInnerElse() { doTest("else"); }
  @Test
  public void testLadderInnerFor() { doTest("for"); }
  @Test
  public void testLadderInnerIf() { doTest("if"); }
  @Test
  public void testLadderOuterElse() { doTest("else"); }
  @Test
  public void testLadderOuterFor() { doTest("for"); }
  @Test
  public void testLadderOuterIf() { doTest("if"); }
  @Test
  public void testLadderOutside() { ig.get().assertQuickfixNotAvailable(getMessagePrefix()); }

  @Test
  public void testNewlineInBlock() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    CodeStyle.getSettings(myFixture.getProject()).getCommonSettings(JavaLanguage.INSTANCE).KEEP_SIMPLE_BLOCKS_IN_ONE_LINE = true;
    doTest("if");
  }

  private void doTest(String keyword) {
    ig.get().doTest(getMessage(keyword));
  }

  private static String getMessage(String keyword) {
    return InspectionGadgetsBundle.message("control.flow.statement.without.braces.message", keyword);
  }

  private static String getMessagePrefix() {
    final String message = InspectionGadgetsBundle.message("control.flow.statement.without.braces.message", "@");
    final int index = message.indexOf("@");
    if (index >= 0) return message.substring(0, index);
    return message;
  }
}
