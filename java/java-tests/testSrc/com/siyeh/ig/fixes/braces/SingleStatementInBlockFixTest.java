// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.braces;

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.util.TextRange;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspection;
import com.siyeh.ig.IGQuickFixesTestCase;
import com.siyeh.ig.style.SingleStatementInBlockInspection;

import java.util.List;

/**
 * @author Bas Leijdekkers
 */
public class SingleStatementInBlockFixTest extends IGQuickFixesTestCase {

  public void testBetweenIfAndElse() { assertQuickfixNotAvailable(getMessagePrefix());}
  public void testIfElse() { doTest("if"); }
  public void testIfElse2() { doTest("if"); }
  public void testIfElse3() { doTest("else"); }
  public void testWhile() { doTest("while"); }
  public void testForEach() { doTest("for"); }
  public void testForIndex() { doTest("for"); }
  public void testForMalformed() { assertQuickfixNotAvailable(getMessage("for"));}
  public void testDoWhile() { doTest("do"); }
  public void testIfWithLoop() { doTest("if"); }
  public void testIfWithLoop2() { doTest("else"); }
  public void testIfWithLoop3() { assertQuickfixNotAvailable(getMessage("else")); }
  public void testIfWithLoop4() { doTest("if"); }
  public void testElseWithLoop() { doTest("else"); }
  public void testTwoComments() { doTest("if"); }
  public void testIncompleteIf() { assertQuickfixNotAvailable(getMessage("for")); }
  public void testNestedFor() { assertQuickfixNotAvailable(getMessage("for")); }
  public void testNestedFor2() { doTest("for"); }
  public void testHighlighting() {
    myFixture.configureByText("Test.java", """
      class X {
        void test(int x) {
         if(x > 0) {
            System.out.println<caret>("Positive");
         }
        }
      }""");
    IntentionAction action = myFixture.findSingleIntention(getMessage("if"));
    ModCommandAction mcAction = action.asModCommandAction();
    assertNotNull(mcAction);
    Presentation presentation =
      mcAction.getPresentation(ActionContext.from(myFixture.getEditor(), myFixture.getFile()));
    assertNotNull(presentation);
    assertEquals(List.of(new Presentation.HighlightRange(TextRange.from(44, 1), EditorColors.DELETED_TEXT_ATTRIBUTES),
      new Presentation.HighlightRange(TextRange.from(87, 1), EditorColors.DELETED_TEXT_ATTRIBUTES)), presentation.rangesToHighlight());
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    myRelativePath = "single_statement_block";
  }

  @Override
  protected BaseInspection getInspection() {
    return new SingleStatementInBlockInspection();
  }

  @Override
  protected void doTest(String keyword) {
    super.doTest(getMessage(keyword));
  }

  private static String getMessage(String keyword) {
    return InspectionGadgetsBundle.message("single.statement.in.block.quickfix", keyword);
  }

  private static String getMessagePrefix() {
    final String message = InspectionGadgetsBundle.message("single.statement.in.block.quickfix", "@");
    final int index = message.indexOf("@");
    if (index >= 0) return message.substring(0, index);
    return message;
  }
}
