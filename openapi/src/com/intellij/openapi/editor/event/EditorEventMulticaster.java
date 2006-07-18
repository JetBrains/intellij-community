/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

/**
 * Allows to attach listeners which receive notifications about changes in any currently open
 * editor.
 *
 * @see com.intellij.openapi.editor.EditorFactory#getEventMulticaster() 
 */
public interface EditorEventMulticaster {
  void addDocumentListener(DocumentListener listener);
  void addDocumentListener(DocumentListener listener, Disposable parentDisposable);
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
