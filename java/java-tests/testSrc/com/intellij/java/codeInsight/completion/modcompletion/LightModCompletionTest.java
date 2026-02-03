// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightModCompletionServiceImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

import java.util.ArrayList;
import java.util.List;

public final class LightModCompletionTest extends LightJavaCodeInsightFixtureTestCase {
  public void testCompletion() {
    myFixture.configureByText("Test.java", """
      public class Test {
        void test() {
          swi<caret>
        }
      }
      """);
    List<ModCompletionItem> result = new ArrayList<>();
    int offset = getEditor().getCaretModel().getOffset();
    LightModCompletionServiceImpl.getItems(
      getFile(), offset, 1, CompletionType.BASIC, result::add);
    assertNotEmpty(result);
    ModCompletionItem first = result.getFirst();
    assertEquals("switch", first.mainLookupString());
    ActionContext context = ActionContext.from(getEditor(), getFile())
      .withSelection(TextRange.create(offset - 3, offset));
    ModCommand command = first.perform(context, ModCompletionItem.DEFAULT_INSERTION_CONTEXT);
    CommandProcessor.getInstance().executeCommand(
      getProject(), () -> {
        WriteAction.run(() -> getFile().getFileDocument().deleteString(offset - 3, offset));
        ModCommandExecutor.getInstance().executeInteractively(context, command, getEditor());
      }, 
      "Test", null);
    myFixture.checkResult("""
                            public class Test {
                              void test() {
                                switch (<caret>)
                              }
                            }
                            """);
  }
}
