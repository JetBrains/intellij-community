// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.openapi.editor.actions;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.editor.impl.EditorModelFixture;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.intellij.openapi.editor.impl.EditorModelFixtureKt.editorModelFixture;
import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class JavaEditorActionTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<EditorModelFixture> editorFixture = editorModelFixture(codeInsightModuleFixture(projectFixture));

  private EditorModelFixture myFixture;

  @BeforeEach
  void setUp() {
    myFixture = editorFixture.get();
  }

  @Test
  public void testDeleteToWordStartWithEscapeChars() {
    myFixture.runTest(() -> {
      myFixture.init("class Foo { String s = \"a\\nb<caret>\"; }", JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("class Foo { String s = \"a\\n<caret>\"; }");
    });
  }

  @Test
  public void testDeleteToWordEndWithEscapeChars() {
    myFixture.runTest(() -> {
      myFixture.init("class Foo { String s = \"a\\<caret>nb\"; }", JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_END);
      myFixture.checkResultByText("class Foo { String s = \"a\\<caret>b\"; }");
    });
  }

  @Test
  public void testToggleCaseForTextAfterEscapedSlash() {
    myFixture.runTest(() -> {
      myFixture.init("class C { String s = \"<selection>ab\\\\cd<caret></selection>\"; }", JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
      myFixture.checkResultByText("class C { String s = \"<selection>AB\\\\CD<caret></selection>\"; }");
    });
  }

  @Test
  public void testToggleCaseForEscapedChar() {
    myFixture.runTest(() -> {
      myFixture.init("class C { String s = \"<selection>ab\\ncd<caret></selection>\"; }", JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_TOGGLE_CASE);
      myFixture.checkResultByText("class C { String s = \"<selection>AB\\nCD<caret></selection>\"; }");
    });
  }

  @Test
  public void testDeleteToWordStartWithEscapedQuote() {
    myFixture.runTest(() -> {
      myFixture.init("class Foo { String s = \"\\\"a<caret>\"; }", JavaFileType.INSTANCE);
      myFixture.executeAction(IdeActions.ACTION_EDITOR_DELETE_TO_WORD_START);
      myFixture.checkResultByText("class Foo { String s = \"\\\"<caret>\"; }");
    });
  }

}
