/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;

import java.util.EventListener;

public interface EditorMouseListener extends EventListener {
  void mousePressed(EditorMouseEvent e);
  void mouseClicked(EditorMouseEvent e);
  void mouseReleased(EditorMouseEvent e);
  void mouseEntered(EditorMouseEvent e);
  void mouseExited(EditorMouseEvent e);
}
