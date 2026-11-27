// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.fixes.braces;

import com.intellij.codeInsight.intention.CustomizableIntentionAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommandAction;
import com.intellij.modcommand.Presentation;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.EditorColors;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.JavaCodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.IGQuickFixesFixture;
import com.siyeh.ig.style.SingleStatementInBlockInspection;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.siyeh.ig.IGQuickFixesFixtureKt.igQuickFixesFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * @author Bas Leijdekkers
 */
@TestApplication
public class SingleStatementInBlockFixTest {
  private final TestFixture<IGQuickFixesFixture> ig =
    igQuickFixesFixture(() -> List.of(new SingleStatementInBlockInspection()), "single_statement_block");

  @Test
  public void testBetweenIfAndElse() { ig.get().assertQuickfixNotAvailable(getMessagePrefix());}
  @Test
  public void testIfElse() { doTest("if"); }
  @Test
  public void testIfElse2() { doTest("if"); }
  @Test
  public void testIfElse3() { doTest("else"); }
  @Test
  public void testWhile() { doTest("while"); }
  @Test
  public void testForEach() { doTest("for"); }
  @Test
  public void testForIndex() { doTest("for"); }
  @Test
  public void testForMalformed() { ig.get().assertQuickfixNotAvailable(getMessage("for"));}
  @Test
  public void testDoWhile() { doTest("do"); }
  @Test
  public void testIfWithLoop() { doTest("if"); }
  @Test
  public void testIfWithLoop2() { doTest("else"); }
  @Test
  public void testIfWithLoop3() { ig.get().assertQuickfixNotAvailable(getMessage("else")); }
  @Test
  public void testIfWithLoop4() { doTest("if"); }
  @Test
  public void testElseWithLoop() { doTest("else"); }
  @Test
  public void testTwoComments() { doTest("if"); }
  @Test
  public void testIncompleteIf() { ig.get().assertQuickfixNotAvailable(getMessage("for")); }
  @Test
  public void testNestedFor() { ig.get().assertQuickfixNotAvailable(getMessage("for")); }
  @Test
  public void testNestedFor2() { doTest("for"); }
  @Test
  public void testFixAll() { ig.get().doTest("Fix all 'Code block contains single statement' problems in file");}
  @Test
  public void testHighlighting() {
    JavaCodeInsightTestFixture myFixture = ig.get().getFixture();
    myFixture.configureByText("Test.java", """
      class X {
        void test(int x) {
         if(x > 0) {
            System.out.println<caret>("Positive");
         }
        }
      }""");
    IntentionAction action = myFixture.findSingleIntention(getMessage("if"));
    ReadAction.runBlocking(() -> {
      ModCommandAction mcAction = action.asModCommandAction();
      assertNotNull(mcAction);
      PsiFile psiFile = myFixture.getFile();
      Presentation presentation =
        mcAction.getPresentation(ActionContext.from(myFixture.getEditor(), psiFile));
      assertNotNull(presentation);
      assertEquals(List.of(new Presentation.HighlightRange(TextRange.from(44, 1), Presentation.HighlightingKind.DELETED_RANGE),
                           new Presentation.HighlightRange(TextRange.from(87, 1), Presentation.HighlightingKind.DELETED_RANGE)),
                   presentation.rangesToHighlight());
      assertInstanceOf(CustomizableIntentionAction.class, action);
      List<CustomizableIntentionAction.RangeToHighlight> ranges =
        ((CustomizableIntentionAction)action).getRangesToHighlight(myFixture.getEditor(), psiFile);
      assertEquals(2, ranges.size());
      assertEquals(
        List.of(TextRange.from(44, 1), TextRange.from(87, 1)),
        List.of(ranges.get(0).getRangeInFile(), ranges.get(1).getRangeInFile()));
      assertEquals(
        List.of(EditorColors.DELETED_TEXT_ATTRIBUTES, EditorColors.DELETED_TEXT_ATTRIBUTES),
        List.of(ranges.get(0).getHighlightKey(), ranges.get(1).getHighlightKey()));
    });
  }

  private void doTest(String keyword) {
    ig.get().doTest(getMessage(keyword));
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
