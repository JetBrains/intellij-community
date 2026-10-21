// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.todo;

import com.intellij.editor.TodoItemsTestUtil;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.impl.OpenProjectTask;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.junit5.fixture.TestFixture;
import org.junit.jupiter.api.Test;

import static com.intellij.platform.testFramework.junit5.codeInsight.fixture.CodeInsightFixtureKt.codeInsightModuleFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.projectFixture;
import static com.intellij.testFramework.junit5.fixture.FixturesKt.tempPathFixture;

@TestApplication
public class JavaTodoTest {
  @SuppressWarnings("deprecation")
  private static final TestFixture<Project> projectFixture = projectFixture(tempPathFixture(), OpenProjectTask.build(), true);

  private final TestFixture<CodeInsightTestFixture> codeInsightFixture = codeInsightModuleFixture(projectFixture);

  private void testTodos(String text) {
    TodoItemsTestUtil.testTodos(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION, text);
  }

  private void checkTodos(String text) {
    TodoItemsTestUtil.checkTodos(codeInsightFixture.get(), text);
  }

  @Test
  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C {} // [TODO todo]\n" +
              "//   unrelated comment line");
  }

  @Test
  public void testNewLineBetweenCommentLines() {
    testTodos("""
                class C {
                    // [TODO first line]<caret>
                    //  [second line]
                }""");
    codeInsightFixture.get().type('\n');
    checkTodos("""
                 class C {
                     // [TODO first line]
                    \s
                     //  second line
                 }""");
  }

  @Test
  public void testNoContinuationOnJaggedLineComments() {
    testTodos("""
                class C {
                  int a; // [TODO something]
                  int ab; // unrelated
                }""");
  }

  @Test
  public void testUnicodeCaseInsensitivePattern() {
    TodoItemsTestUtil.doWithPattern("\u00f6.*", () -> testTodos("// [\u00d6 something]"));
  }

  @Test
  public void testPatternIncludingCommentPrefix() {
    TodoItemsTestUtil.doWithPattern("//foo.*", () -> testTodos("//[foo]"));
  }

  @Test
  public void testSuccessiveLineComments() {
    TodoItemsTestUtil.doTestSuccessiveLineComments(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }

  @Test
  public void testSuccessiveLineCommentsAfterEditing() {
    TodoItemsTestUtil.doTestSuccessiveLineCommentsAfterEditing(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }

  @Test
  public void testNewTodoAfterUnsavedEditing() {
    TodoItemsTestUtil.doTestNewTodoAfterUnsavedEditing(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }

  @Test
  public void testAllLinesLoseHighlightingWithFirstLine() {
    TodoItemsTestUtil.doTestAllLinesLoseHighlightingWithFirstLine(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }

  @Test
  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    TodoItemsTestUtil.doTestContinuationIsNotOverlappedWithFollowingTodo(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }

  @Test
  public void testContinuationInBlockCommentWithStars() {
    TodoItemsTestUtil.doTestContinuationInBlockCommentWithStars(codeInsightFixture.get(), JavaFileType.DEFAULT_EXTENSION);
  }
}
