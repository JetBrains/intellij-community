/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
  void removeSelectionListener(@NotNull SelectionListener listener);

  void addVisibleAreaListener(@NotNull VisibleAreaListener listener);
  void removeVisibleAreaListener(@NotNull VisibleAreaListener listener);
}
