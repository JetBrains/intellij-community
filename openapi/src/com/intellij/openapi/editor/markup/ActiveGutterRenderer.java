/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;

import java.awt.event.MouseEvent;

/**
 * @author max
 */
public interface ActiveGutterRenderer extends LineMarkerRenderer {
  void doAction(Editor editor, MouseEvent e);
}
