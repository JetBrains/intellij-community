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
package com.intellij.ide.structureView;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.fileEditor.FileEditor;

import javax.swing.*;

/**
 * Defines the implementation of a custom structure view or file structure popup component.
 * The structure view is linked to a file editor and displays the structure of the file
 * contained in that editor.
 *
 * @see StructureViewBuilder#createStructureView(com.intellij.openapi.fileEditor.FileEditor, com.intellij.openapi.project.Project)
 * @see TreeBasedStructureViewBuilder
 */

public interface StructureView extends Disposable {
  /**
   * Returns the editor whose structure is displayed in the structure view.
   *
   * @return the editor linked to the structure view.
   */
  FileEditor getFileEditor();

  /**
   * Selects the element which corresponds to the current cursor position in the editor
   * linked to the structure view.
   *
   * @param requestFocus if true, the structure view component also grabs the focus.
   */
  // TODO: drop return value?
  boolean navigateToSelectedElement(boolean requestFocus);

  /**
   * Returns the Swing component representing the structure view.
   *
   * @return the structure view component.
   */
  JComponent getComponent();

  // TODO: remove from OpenAPI?
  void centerSelectedRow();

  /**
   * Restores the state of the structure view (the expanded and selected elements)
   * from the user data of the file editor to which it is linked.
   *
   * @see FileEditor#getUserData(com.intellij.openapi.util.Key)
   */
  void restoreState();

  /**
   * Stores the state of the structure view (the expanded and selected elements)
   * in the user data of the file editor to which it is linked.
   *
   * @see FileEditor#putUserData(com.intellij.openapi.util.Key, Object)
   */
  void storeState();
}
