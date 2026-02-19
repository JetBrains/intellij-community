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
  /**
   * @deprecated Use {@link #addDocumentListener(DocumentListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated
  void addDocumentListener(@NotNull DocumentListener listener);

  void addDocumentListener(@NotNull DocumentListener listener, @NotNull Disposable parentDisposable);

  void removeDocumentListener(@NotNull DocumentListener listener);

  /**
   * @deprecated Use {@link #addEditorMouseListener(EditorMouseListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated
  void addEditorMouseListener(@NotNull EditorMouseListener listener);

  void addEditorMouseListener(@NotNull EditorMouseListener listener, @NotNull Disposable parentDisposable);

  void removeEditorMouseListener(@NotNull EditorMouseListener listener);

  /**
   * @deprecated Use {@link #addEditorMouseMotionListener(EditorMouseMotionListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated
  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  void addEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener, @NotNull Disposable parentDisposable);

  void removeEditorMouseMotionListener(@NotNull EditorMouseMotionListener listener);

  /**
   * @deprecated Use {@link #addCaretListener(CaretListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated
  void addCaretListener(@NotNull CaretListener listener);

  void addCaretListener(@NotNull CaretListener listener, @NotNull Disposable parentDisposable);

  void removeCaretListener(@NotNull CaretListener listener);

  /**
   * @deprecated Use {@link #addSelectionListener(SelectionListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated(forRemoval = true)
  void addSelectionListener(@NotNull SelectionListener listener);

  void addSelectionListener(@NotNull SelectionListener listener, @NotNull Disposable parentDisposable);

  void removeSelectionListener(@NotNull SelectionListener listener);

  /**
   * @deprecated Use {@link #addVisibleAreaListener(VisibleAreaListener, Disposable)} instead to avoid leaking listeners
   */
  @Deprecated
  void addVisibleAreaListener(@NotNull VisibleAreaListener listener);

  default void addVisibleAreaListener(@NotNull VisibleAreaListener listener, @NotNull Disposable parent) {
    throw new IllegalStateException("Not implemented");
  }

  void removeVisibleAreaListener(@NotNull VisibleAreaListener listener);
}
