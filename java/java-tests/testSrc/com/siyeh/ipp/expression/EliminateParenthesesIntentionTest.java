// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ipp.expression;

import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.editor.CaretModel;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.siyeh.ipp.IPPFixture;
import com.siyeh.ipp.expression.eliminate.EliminateParenthesesIntention;
import org.junit.jupiter.api.Test;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static com.siyeh.ipp.IPPFixtureKt.ippFixture;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestApplication
public class EliminateParenthesesIntentionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<IPPFixture> ipp = ippFixture(projectFixture, "expression/eliminate_parentheses");

  @Test
  public void testAssociative() {
    doTest();
  }

  @Test
  public void testDistributive() {
    doTest();
  }

  @Test
  public void testNestedParenthesis() {
    doTest();
  }

  private void doTest() {
    LightJavaCodeInsightTestFixture myFixture = ipp.get().getFixture();
    String testName = myFixture.getTestName(false);
    myFixture.configureByFile(testName + ".java");
    EdtTestUtil.runInEdtAndWait(() -> {
      CaretModel model = myFixture.getEditor().getCaretModel();
      EliminateParenthesesIntention intention = new EliminateParenthesesIntention();
      model.runForEachCaret(caret -> {
        ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
        assertNotNull(intention.getPresentation(context));
        ModCommand command = intention.perform(context);
        CommandProcessor.getInstance().executeCommand(
          myFixture.getProject(),
          () -> ModCommandExecutor.getInstance().executeInBatch(context, command),
          "", null);
        NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
      });
      myFixture.checkResultByFile(testName + "_after.java", false);
    });
  }
}
