/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor.event;



public interface EditorEventMulticaster {
  void addDocumentListener(DocumentListener listener);
  void removeDocumentListener(DocumentListener listener);

  void addEditorMouseListener(EditorMouseListener listener);
  void removeEditorMouseListener(EditorMouseListener listener);

  void addEditorMouseMotionListener(EditorMouseMotionListener listener);
  void removeEditorMouseMotionListener(EditorMouseMotionListener listener);

  void addCaretListener(CaretListener listener);
  void removeCaretListener(CaretListener listener);

  void addSelectionListener(SelectionListener listener);
  void removeSelectionListener(SelectionListener listener);

  void addVisibleAreaListener(VisibleAreaListener listener);
  void removeVisibleAreaListener(VisibleAreaListener listener);
}
