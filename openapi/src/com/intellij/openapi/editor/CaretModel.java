/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.editor;

import com.intellij.openapi.editor.event.CaretListener;

public interface CaretModel {
  void moveCaretRelatively(int columnShift,
                           int lineShift,
                           boolean withSelection,
                           boolean blockSelection,
                           boolean scrollToCaret);

  void moveToLogicalPosition(LogicalPosition pos);
  void moveToVisualPosition(VisualPosition pos);
  void moveToOffset(int offset);

  LogicalPosition getLogicalPosition();
  VisualPosition getVisualPosition();
  int getOffset();

  void addCaretListener(CaretListener listener);
  void removeCaretListener(CaretListener listener);
}
