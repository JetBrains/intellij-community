/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.actionSystem;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;

public interface TypedActionHandler {
  void execute(Editor editor, char charTyped, DataContext dataContext);
}
