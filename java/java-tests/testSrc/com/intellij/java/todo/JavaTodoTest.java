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
    testTodos("""
                class C {
                    // [TODO first line]<caret>
                    //  [second line]
                }""");
    type('\n');
    checkTodos("""
                 class C {
                     // [TODO first line]
                    \s
                     //  second line
                 }""");
  }

  public void testNoContinuationOnJaggedLineComments() {
    testTodos("""
                class C {
                  int a; // [TODO something]
                  int ab; // unrelated
                }""");
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
