/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

import java.awt.*;

public interface LineMarkerRenderer {
  void paint(Editor editor, Graphics g, Rectangle r);
}