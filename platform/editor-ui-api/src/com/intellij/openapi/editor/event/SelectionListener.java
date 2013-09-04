/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

import java.util.EventListener;

/**
 * Allows to receive information about selection changes in an editor.
 *
 * @see com.intellij.openapi.editor.SelectionModel#addSelectionListener(SelectionListener)
 * @see EditorEventMulticaster#addSelectionListener(SelectionListener)
 */
public interface SelectionListener extends EventListener {
  /**
   * Called when the selected area in an editor is changed.
   *
   * @param e the event containing information about the change.
   */
  void selectionChanged(SelectionEvent e);
}
