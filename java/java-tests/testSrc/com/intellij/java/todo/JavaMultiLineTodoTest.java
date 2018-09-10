// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.todo;

import com.intellij.codeInsight.daemon.todo.TodoItemsTestCase;
import com.intellij.ide.highlighter.JavaFileType;

public class JavaMultiLineTodoTest extends TodoItemsTestCase {
  public void testSuccessiveLineComments() {
    testTodos("// [TODO first line]\n" +
              "//      [second line]");
  }

  public void testSuccessiveLineCommentsAfterEditing() {
    testTodos("// [TODO first line]\n" +
              "// <caret>second line");
    type("     ");
    checkTodos("// [TODO first line]\n" +
               "//      [second line]");
  }

  public void testAllLinesLoseHighlightingWithFirstLine() {
    testTodos("// [TO<caret>DO first line]\n" +
              "//      [second line]");
    delete();
    checkTodos("// TOO first line\n" +
               "//      second line");
  }

  public void testContinuationIsNotOverlappedWithFollowingTodo() {
    testTodos("// [TODO first line]\n" +
              "//  [TODO second line]");
  }

  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C {} // [TODO todo]\n" +
              "//   unrelated comment line");
  }

  public void testContinuationInBlockCommentWithStars() {
    testTodos("/*\n" +
              " * [TODO first line]\n" +
              " *  [second line]\n" +
              " */");
  }

  public void testNewLineBetweenCommentLines() {
    testTodos("class C {\n" +
              "    // [TODO first line]<caret>\n" +
              "    //  [second line]\n" +
              "}");
    type('\n');
    checkTodos("class C {\n" +
               "    // [TODO first line]\n" +
               "    \n" +
               "    //  second line\n" +
               "}");
  }

  @Override
  protected String getFileExtension() {
    return JavaFileType.DEFAULT_EXTENSION;
  }
}
