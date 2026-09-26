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
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;

public class NotNullPostfixTemplateUndoTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNnExpansionIsUndoneInOneStep() {
    myFixture.configureByText("a.java", """
      public class Foo {
          void m(Object o) {
              o.nn<caret>
          }
      }
      """);
    FileEditor fileEditor = openEditorForUndo();

    expandModPostfixTemplate();

    String expanded = myFixture.getEditor().getDocument().getText();
    assertTrue("Template was not expanded, got:\n" + expanded, expanded.contains("if (o != null)"));

    UndoManager.getInstance(getProject()).undo(fileEditor);
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();

    assertEquals("""
                   public class Foo {
                       void m(Object o) {
                           o.nn
                       }
                   }
                   """,
                 myFixture.getEditor().getDocument().getText());
  }

  private void expandModPostfixTemplate() {
    TemplateManagerImpl.setTemplateTesting(getTestRootDisposable());
    Registry.get("postfix.template.mod.completion.enabled").setValue(true, getTestRootDisposable());
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
      LookupManager.getInstance(getProject()).hideActiveLookup();
      myFixture.type("\t");
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    TemplateState state = TemplateManagerImpl.getTemplateState(myFixture.getEditor());
    if (state != null) {
      WriteCommandAction.runWriteCommandAction(getProject(), () -> state.gotoEnd(false));
    }
    NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
  }

  private FileEditor openEditorForUndo() {
    Ref<FileEditor> editor = Ref.create();
    editor.set(FileEditorManager.getInstance(getProject()).openFile(myFixture.getFile().getVirtualFile(), false)[0]);
    UndoManagerImpl manager = (UndoManagerImpl)UndoManager.getInstance(getProject());
    manager.setOverriddenEditorProvider(new CurrentEditorProvider() {
      @Override
      public FileEditor getCurrentEditor(@Nullable Project project) {
        return editor.get();
      }
    });
    disposeOnTearDown(() -> manager.setOverriddenEditorProvider(null));
    return editor.get();
  }
}
