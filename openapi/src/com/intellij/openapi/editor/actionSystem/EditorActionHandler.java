/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;

public abstract class EditorActionHandler {
  public boolean isEnabled(Editor editor, DataContext dataContext) {
    return true;
  }

  public abstract void execute(Editor editor, DataContext dataContext);
}
