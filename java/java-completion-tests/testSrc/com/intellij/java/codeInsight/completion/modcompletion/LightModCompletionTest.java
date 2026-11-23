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
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;

@TestApplication
@IndexingModeTemplate
public final class LightModCompletionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testCompletion() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          void test() {
            swi<caret>
          }
        }
        """);
      List<ModCompletionItem> result = new ArrayList<>();
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      LightModCompletionServiceImpl.getItems(
        myFixture.getFile(), offset, 1, CompletionType.BASIC, result::add);
      UsefulTestCase.assertNotEmpty(result);
      ModCompletionItem first = result.getFirst();
      assertEquals("switch", first.mainLookupString());
      ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile())
        .withSelection(TextRange.create(offset - 3, offset));
      ModCommand command = first.perform(context, ModCompletionItem.DEFAULT_INSERTION_CONTEXT);
      CommandProcessor.getInstance().executeCommand(
        myFixture.getProject(), () -> {
          WriteAction.run(() -> myFixture.getFile().getFileDocument().deleteString(offset - 3, offset));
          ModCommandExecutor.getInstance().executeInteractively(context, command, myFixture.getEditor());
        }, 
        "Test", null);
      myFixture.checkResult("""
                              public class Test {
                                void test() {
                                  switch (<caret>)
                                }
                              }
                              """);
    });
  }
}
