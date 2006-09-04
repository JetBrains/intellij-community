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
import com.intellij.peer.PeerFactory;


/**
 * Default implementation of the {@link StructureViewBuilder} interface which uses the
 * standard IDEA implementation of the {@link StructureView} component and allows to
 * customize the data displayed in the structure view.
 *
 * @see StructureViewModel
 * @see TextEditorBasedStructureViewModel
 * @see com.intellij.lang.Language#getStructureViewBuilder(com.intellij.psi.PsiFile)
 * @see com.intellij.openapi.fileTypes.FileType#getStructureViewBuilder(com.intellij.openapi.vfs.VirtualFile, com.intellij.openapi.project.Project)
 */
public abstract class TreeBasedStructureViewBuilder implements StructureViewBuilder {
  /**
   * Returns the structure view model defining the data displayed in the structure view
   * for a specific file.
   *
   * @return the structure view model instance.
   * @see TextEditorBasedStructureViewModel
   */
  public abstract StructureViewModel createStructureViewModel();

  public StructureView createStructureView(FileEditor fileEditor, Project project) {
    return PeerFactory.getInstance().getStructureViewFactory().createStructureView(fileEditor, createStructureViewModel(), project, isRootNodeShown());
  }

  /**
   * Override returning <code>false</code> if root node created by {@link #createStructureViewModel()} shall not be visible
   * @return <code>false</code> if root node shall not be visible in structure tree.
   */
  public boolean isRootNodeShown() {
    return true;
  }
}
