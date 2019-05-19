// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.event;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * Allows to attach listeners which receive notifications about changes in any currently open
 * editor.
 *
 * @see com.intellij.openapi.editor.EditorFactory#getEventMulticaster()
 */
public interface EditorEventMulticaster {
  void addDocumentListener(@NotNull DocumentListener listener);

  void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable);

  void removeDocumentListener(@NotNull DocumentListener listener);

  void addEditorMouseListener(@NotNull EditorMouseListener listener);

  void addEditorMouseListener(@NotNull EditorMouseListener listener, @NotNull Disposable parentDisposable);

  void removeEditorMouseListener(@NotNull EditorMouseListener listener);

  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable);

  void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  void addCaretListener(@NotNull CaretListener listener);

  void addCaretListener(@NotNull CaretListener listener, @NotNull Disposable parentDisposable);

  void removeCaretListener(@NotNull CaretListener listener);

  void addSelectionListener(@NotNull SelectionListener listener);

  void addSelectionListener(@NotNull SelectionListener listener, @NotNull Disposable parentDisposable);

  void removeSelectionListener(@NotNull SelectionListener listener);

  void addVisibleAreaListener(@NotNull VisibleAreaListener listener);

  void removeVisibleAreaListener(@NotNull VisibleAreaListener listener);
}
