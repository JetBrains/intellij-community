// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.todo;

import com.intellij.editor.TodoItemsTestCase;
import com.intellij.ide.highlighter.JavaFileType;

public class JavaTodoTest extends TodoItemsTestCase {
  public void testNoContinuationWithoutProperIndent() {
    testTodos("class C {} // [TODO todo]\n" +
              "//   unrelated comment line");
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

  public void testNoContinuationOnJaggedLineComments() {
    testTodos("class C {\n" +
              "  int a; // [TODO something]\n" +
              "  int ab; // unrelated\n" +
              "}");
  }

  public void testUnicodeCaseInsensitivePattern() {
    doWithPattern("\u00f6.*", () -> testTodos("// [\u00d6 something]"));
  }

  public void testPatternIncludingCommentPrefix() {
    doWithPattern("//foo.*", () -> testTodos("//[foo]"));
  }

  @Override
  protected String getFileExtension() {
    return JavaFileType.DEFAULT_EXTENSION;
  }

  @Override
  protected boolean supportsCStyleSingleLineComments() {
    return true;
  }

  @Override
  protected boolean supportsCStyleMultiLineComments() {
    return true;
  }
}
