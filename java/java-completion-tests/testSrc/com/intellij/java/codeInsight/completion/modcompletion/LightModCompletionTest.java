// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.completion.modcompletion;

import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.LightModCompletionServiceImpl;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcompletion.CommonCompletionItem;
import com.intellij.modcompletion.ModCompletionItem;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.util.TextRange;
import com.intellij.platform.testFramework.junit5.codeInsight.indexing.IndexingModeTemplate;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.NeedsIndex;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
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

  @Test
  public void testCommonItemWithoutUpdateMatchesPsiUpdate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.configureByText("Test.java", """
        public class Test {
          void test() {
            int x = fo<caret>oBar;
          }
        }
        """);
      int offset = myFixture.getEditor().getCaretModel().getOffset();
      ActionContext context = ActionContext.from(myFixture.getEditor(), myFixture.getFile());
      List<ActionContext> contexts = List.of(
        context,
        context.withOffset(offset - 2).withSelection(TextRange.create(offset - 2, offset)));
      List<ModCompletionItem.InsertionContext> insertionContexts = List.of(
        ModCompletionItem.DEFAULT_INSERTION_CONTEXT,
        new ModCompletionItem.InsertionContext(ModCompletionItem.InsertionMode.INSERT, '('),
        new ModCompletionItem.InsertionContext(ModCompletionItem.InsertionMode.OVERWRITE, '	'),
        new ModCompletionItem.InsertionContext(ModCompletionItem.InsertionMode.OVERWRITE, ';'));
      CommonCompletionItem fastItem = new CommonCompletionItem("foo");
      // A no-op updater makes the item use ModCommand.psiUpdate
      CommonCompletionItem psiUpdateItem = fastItem.withAdditionalUpdater((start, updater) -> { });
      for (ActionContext actionContext : contexts) {
        for (ModCompletionItem.InsertionContext insertionContext : insertionContexts) {
          ModCommand expected = psiUpdateItem.perform(actionContext, insertionContext);
          ModCommand actual = fastItem.perform(actionContext, insertionContext);
          assertEquals(expected, actual, () -> actionContext + "; " + insertionContext);
        }
      }
    });
  }

  @Test
  @NeedsIndex.Full(reason = "The import resolves p.Foo, a project class")
  public void testStaticImportOrder() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    EdtTestUtil.runInEdtAndWait(() -> {
      myFixture.addClass("""
        package p;
        public class Foo {
          public static void zeta() {}
          public static void beta() {}
          public static int ALPHA;
        }
        """);
      myFixture.configureByText("Test.java", """
        import static p.Foo.<caret>
        public class Test {}
        """);
      List<ModCompletionItem> result = new ArrayList<>();
      LightModCompletionServiceImpl.getItems(
        myFixture.getFile(), myFixture.getEditor().getCaretModel().getOffset(), 1, CompletionType.BASIC, result::add);
      assertEquals(List.of("*", "beta", "zeta", "ALPHA"), ContainerUtil.map(result, ModCompletionItem::mainLookupString));
    });
  }
}
