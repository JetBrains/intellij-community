// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.siyeh.IntentionPowerPackBundle;
import com.siyeh.ipp.IPPTestCase;
import com.siyeh.ipp.expression.eliminate.EliminateParenthesesIntention;
import org.jetbrains.annotations.NotNull;

public class EliminateParenthesesIntentionTest extends IPPTestCase {

  public void testAssociative() {
    doTest();
  }

  public void testDistributive() {
    doTest();
  }

  public void testNestedParenthesis() {
    doTest();
  }

  @Override
  protected void doTest(@NotNull String intentionName) {
    String testName = getTestName(false);
    myFixture.configureByFile(testName + ".java");
    CaretModel model = myFixture.getEditor().getCaretModel();
    EliminateParenthesesIntention intention = new EliminateParenthesesIntention();
    model.runForEachCaret(caret -> {
      ActionContext context = ActionContext.from(getEditor(), getFile());
      assertNotNull(intention.getPresentation(context));
      ModCommand command = intention.perform(context);
      CommandProcessor.getInstance().executeCommand(
        getProject(),
        () -> ModCommandExecutor.getInstance().executeInBatch(context, command),
        "", null);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    });
    myFixture.checkResultByFile(testName + "_after.java", false);
  }

  @Override
  protected String getIntentionName() {
    return IntentionPowerPackBundle.message("eliminate.parentheses.intention.name");
  }

  @Override
  protected String getRelativePath() {
    return "expression/eliminate_parentheses";
  }
}
