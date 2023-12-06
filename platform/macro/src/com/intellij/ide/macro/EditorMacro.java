// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.macro;

import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorCoreUtil;
import com.intellij.openapi.editor.LogicalPosition;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public abstract class EditorMacro extends Macro {
  private final String myName;
  private final @Nls String myDescription;

  public EditorMacro(@NotNull String name, @NotNull @Nls String description) {
    myName = name;
    myDescription = description;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @NotNull
  @Override
  public String getDescription() {
    return myDescription;
  }

  @Override
  public final String expand(@NotNull DataContext dataContext) {
    Editor editor = CommonDataKeys.EDITOR.getData(dataContext);
    if (editor != null){
      return expand(editor);
    }
    return null;
  }

  /**
   * @return 1-based column index where tabs are treated as single characters. External tools don't know about IDEA's tab size.
   */
  protected static String getColumnNumber(Editor editor, LogicalPosition pos) {
    if (EditorCoreUtil.inVirtualSpace(editor, pos)) {
      return String.valueOf(pos.column + 1);
    }

    int offset = editor.logicalPositionToOffset(pos);
    return getColumnNumber(editor, offset);
  }

  @NotNull
  protected static String getColumnNumber(Editor editor, int offset) {
    int lineNumber = getLineNumber(editor, offset);
    int lineStart = editor.getDocument().getLineStartOffset(lineNumber);
    return String.valueOf(offset - lineStart + 1);
  }

  protected static int getLineNumber(Editor editor, int offset) {
    return editor.getDocument().getLineNumber(offset);
  }

  @Nullable
  protected abstract String expand(Editor editor);
}
