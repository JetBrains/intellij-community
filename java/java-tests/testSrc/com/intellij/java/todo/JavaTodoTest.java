// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.todo;

import com.intellij.editor.TodoItemsTestCase;
import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.todo.TodoConfiguration;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.search.TodoAttributes;
import com.intellij.psi.search.TodoPattern;
import com.intellij.util.ArrayUtil;

public class JavaTodoTest extends TodoItemsTestCase {
  public void testUnicodeCaseInsensitivePattern() {
    TodoConfiguration todoConfiguration = TodoConfiguration.getInstance();
    TodoPattern[] originalPatterns = todoConfiguration.getTodoPatterns();
    try {
      TodoPattern pattern = new TodoPattern("\u00f6.*", new TodoAttributes(new TextAttributes()), false);
      todoConfiguration.setTodoPatterns(ArrayUtil.append(originalPatterns, pattern));

      testTodos("// [\u00d6 something]");
    }
    finally {
      todoConfiguration.setTodoPatterns(originalPatterns);
    }
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
