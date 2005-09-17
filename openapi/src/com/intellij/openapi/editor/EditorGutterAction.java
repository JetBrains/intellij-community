/*
 * Copyright (c) 2005 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.openapi.editor;

import java.awt.*;

public interface EditorGutterAction {
  void doAction(int lineNum);

  Cursor getCursor(final int lineNum);
}
