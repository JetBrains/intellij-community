// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.completion.CompletionItemLookupElement;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.template.impl.TemplateManagerImpl;
import com.intellij.codeInsight.template.impl.TemplateState;
import com.intellij.codeInsight.template.postfix.completion.PostfixTemplateModCompletionItemProvider;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.command.impl.UndoManagerImpl;
import com.intellij.openapi.command.undo.UndoManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.CurrentEditorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightTestFixture;
import com.intellij.testFramework.fixtures.LightJavaProject;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaCodeInsightFixture;
import static com.intellij.testFramework.fixtures.LightJavaFixturesKt.lightJavaProjectFixture;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class NotNullPostfixTemplateUndoTest {
  private static final TestFixture<LightJavaProject> projectFixture = lightJavaProjectFixture();
  private final TestFixture<LightJavaCodeInsightTestFixture> codeInsightFixture = lightJavaCodeInsightFixture(projectFixture);

  @Test
  public void testNnExpansionIsUndoneInOneStep() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    myFixture.configureByText("a.java", """
      public class Foo {
          void m(Object o) {
              o.nn<caret>
          }
      }
      """);
    EdtTestUtil.runInEdtAndWait(() -> {
      FileEditor fileEditor = openEditorForUndo();

      expandModPostfixTemplate();

      String expanded = myFixture.getEditor().getDocument().getText();
      assertTrue(expanded.contains("if (o != null)"), "Template was not expanded, got:\n" + expanded);

      UndoManager.getInstance(myFixture.getProject()).undo(fileEditor);
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

      assertEquals("""
                     public class Foo {
                         void m(Object o) {
                             o.nn
                         }
                     }
                     """,
                   myFixture.getEditor().getDocument().getText());
    });
  }

  private void expandModPostfixTemplate() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    TemplateManagerImpl.setTemplateTesting(myFixture.getTestRootDisposable());
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, myFixture.getTestRootDisposable());
    LookupElement[] elements = myFixture.completeBasic();
    String substring = myFixture.getFile().getFileDocument().getText().substring(0, myFixture.getCaretOffset());
    List<LookupElement> list = Arrays.stream(elements).filter(
        t -> t instanceof CompletionItemLookupElement completionItemLookupElement &&
             completionItemLookupElement.item() instanceof PostfixTemplateModCompletionItemProvider.PostfixModCompletionItem modCompletionItem &&
             substring.endsWith(((PostfixTemplate)modCompletionItem.contextObject()).getKey()))
      .toList();
    if (list.size() == 1) {
      myFixture.getLookup().setCurrentItem(list.getFirst());
      myFixture.type("\t");
    }
    else if (list.isEmpty()) {
      LookupManager.getInstance(myFixture.getProject()).hideActiveLookup();
      myFixture.type("\t");
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(myFixture.getProject(), () -> state.gotoEnd(false));
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  private FileEditor openEditorForUndo() {
    LightJavaCodeInsightTestFixture myFixture = codeInsightFixture.get();
    Ref<FileEditor> editor = Ref.create();
    editor.set(FileEditorManager.getInstance(myFixture.getProject()).openFile(myFixture.getFile().getVirtualFile(), false)[0]);
    UndoManagerImpl manager = (UndoManagerImpl)UndoManager.getInstance(myFixture.getProject());
    manager.setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return editor.get();
      }
    });
    Disposer.register(myFixture.getTestRootDisposable(), () -> manager.setOverriddenEditorProvider(null));
    return editor.get();
  }
}
