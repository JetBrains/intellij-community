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

import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.project.Project;

/**
 * Factory interface for creating instances of the standard structure view component.
 *
 * @see com.intellij.peer.PeerFactory#getStructureViewFactory()
 */
public interface StructureViewFactory {
  /**
   * Creates a structure view component instance for the specified editor.
   *
   * @param fileEditor the editor to which the structure view is linked.
   * @param treeModel  the model defining the data shown in the structure view.
   * @param project    the project containing the file for which the structure view is requested.
   * @return the structure view instance.
   */
  StructureView createStructureView(FileEditor fileEditor,
                                    StructureViewModel treeModel,
                                    Project project); 

  /**
   * Creates a structure view component instance for the specified editor.
   *
   * @param fileEditor the editor to which the structure view is linked.
   * @param treeModel  the model defining the data shown in the structure view.
   * @param project    the project containing the file for which the structure view is requested.
   * @param showRootNode pass <code>false</code> if root node of the structure built should not actually be shown in result tree.
   * @return the structure view instance.
   */
  StructureView createStructureView(FileEditor fileEditor,
                                    StructureViewModel treeModel,
                                    Project project,
                                    boolean showRootNode);
}
