// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.codeInsight.daemon;

import com.intellij.codeInsight.daemon.impl.GotoNextErrorHandler;
import com.intellij.codeInsight.daemon.impl.actions.GotoNextErrorAction;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.ActionUiKind;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.SimpleDataContext;
import com.intellij.openapi.editor.elf.ElfFeatureFlag;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;

public final class LightErrorNavigationTest extends LightJavaCodeInsightFixtureTestCase {
  public void testNavigateIntoParentheses() {
    myFixture.configureByText("Foo.java", """
      <caret>class Foo {
        void test(int x) {
      
        }
      
        void use() {
            test<error descr="Expected 1 argument but found 0">()</error>;
        }
      }
      """);
    myFixture.testHighlighting(true, false, false);
    assertNextErrorPosition("""
      class Foo {
        void test(int x) {
      
        }
      
        void use() {
            test(<caret>);
        }
      }
      """);
  }

  public void testGotoNextErrorActionIsEnabledWithLockFreeTypingBeforeElfEdits() {
    ElfFeatureFlag.withEnabled(() -> {
      myFixture.configureByText("Foo.java", """
        <caret>class Foo {
          void test(int x) {

          }

          void use() {
              test<error descr="Expected 1 argument but found 0">()</error>;
          }
        }
        """);
      myFixture.testHighlighting(true, false, false);
      GotoNextErrorAction action = new GotoNextErrorAction();
      DataContext dataContext = SimpleDataContext.builder()
        .add(CommonDataKeys.PROJECT, getProject())
        .add(CommonDataKeys.EDITOR, myFixture.getEditor())
        .build();
      AnActionEvent event = AnActionEvent.createEvent(action, dataContext, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null);
      action.update(event);
      assertTrue(event.getPresentation().isEnabled());
    });
  }

  private void assertNextErrorPosition(String expected) {
    new GotoNextErrorHandler(true).invoke(getProject(), myFixture.getEditor(), myFixture.getFile());
    int offset = myFixture.getCaretOffset();
    String text = myFixture.getEditor().getDocument().getText();
    String expectedPos = text.substring(0, offset) + "<caret>" + text.substring(offset);
    assertEquals(expected, expectedPos);
  }
}
