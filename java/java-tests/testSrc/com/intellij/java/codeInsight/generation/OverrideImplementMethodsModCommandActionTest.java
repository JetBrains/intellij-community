// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.generation;

import com.intellij.codeInsight.generation.OverrideImplementMethodsModCommandAction;
import com.intellij.ide.util.MemberChooser;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModEditOptions;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@TestApplication
public final class OverrideImplementMethodsModCommandActionTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void implementInNewExpression() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        void test() {
          Object o = new I<String>()<caret>;
        }
      }
      interface I<T> {
        void foo(T t);

        int bar();
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      fixture.launchAction(OverrideImplementMethodsModCommandAction.implement(findNewExpression(fixture)).asIntention());
      fixture.checkResult("""
        class Test {
          void test() {
            Object o = new I<String>() {
                @Override
                public void foo(String s) {
                   \s
                }

                @Override
                public int bar() {
                    return 0;
                }
            };
          }
        }
        interface I<T> {
          void foo(T t);

          int bar();
        }
        """);
    });
  }

  @Test
  public void notAvailableInIncompleteNewExpression() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    fixture.configureByText("Test.java", """
      class Test {
        void test() {
          new I<String>(new String(<caret>
        }
      }
      interface I<T> {
        void foo(T t);
      }
      """);
    ReadAction.runBlocking(() -> {
      PsiNewExpression newExpression = findNewExpression(fixture);
      ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
      assertNull(OverrideImplementMethodsModCommandAction.implement(newExpression).getPresentation(context));
    });
  }

  @Test
  public void storeCopyJavadoc() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.unsetValue(MemberChooser.PROP_COPYJAVADOC);
    try {
      fixture.configureByText("Test.java", """
        class Test implements Runnable {<caret>
        }
        """);
      Pair<ActionContext, ModCommand> pair = ReadAction.computeBlocking(() -> {
        ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
        ModCommand command = OverrideImplementMethodsModCommandAction.implement().perform(context);
        ModEditOptions<?> editOptions = assertInstanceOf(ModEditOptions.class, ContainerUtil.findLast(command.unpack(), c -> c instanceof ModEditOptions));
        return Pair.create(context, editOptions.applyOptions(Map.of("copyJavadoc", true)));
      });
      EdtTestUtil.runInEdtAndWait(() -> {
        CommandProcessor.getInstance().executeCommand(
          fixture.getProject(),
          () -> ModCommandExecutor.getInstance().executeInteractively(pair.first, pair.second, fixture.getEditor()),
          null, null);
        assertEquals("true", properties.getValue(MemberChooser.PROP_COPYJAVADOC));

        UndoManager.getInstance(fixture.getProject()).undo(TextEditorProvider.getInstance().getTextEditor(fixture.getEditor()));
        assertNull(properties.getValue(MemberChooser.PROP_COPYJAVADOC));
      });
    }
    finally {
      properties.unsetValue(MemberChooser.PROP_COPYJAVADOC);
    }
  }

  @Test
  public void formShowsStoredCopyJavadoc() {
    LightJavaCodeInsightTestFixture fixture = codeInsightFixture.get();
    PropertiesComponent properties = PropertiesComponent.getInstance();
    properties.setValue(MemberChooser.PROP_COPYJAVADOC, "true");
    try {
      fixture.configureByText("Test.java", """
        class Test implements Runnable {<caret>
        }
        """);
      ReadAction.runBlocking(() -> {
        ActionContext context = ActionContext.from(fixture.getEditor(), fixture.getFile());
        ModCommand command = OverrideImplementMethodsModCommandAction.implement().perform(context);
        ModEditOptions<?> editOptions = assertInstanceOf(ModEditOptions.class, ContainerUtil.findLast(command.unpack(), c -> c instanceof ModEditOptions));
        assertEquals(true, editOptions.containerSupplier().get().getOptionController().getOption("copyJavadoc"));
      });
    }
    finally {
      properties.unsetValue(MemberChooser.PROP_COPYJAVADOC);
    }
  }

  private static @NotNull PsiNewExpression findNewExpression(@NotNull LightJavaCodeInsightTestFixture fixture) {
    PsiNewExpression newExpression = ReadAction.computeBlocking(
      () -> PsiTreeUtil.findChildOfType(fixture.getFile(), PsiNewExpression.class));
    assertNotNull(newExpression);
    return newExpression;
  }
}
