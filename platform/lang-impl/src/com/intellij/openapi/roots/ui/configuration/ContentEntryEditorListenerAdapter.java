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

package com.intellij.openapi.roots.ui.configuration;

import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @author Eugene Zhuravlev
 *         Date: Oct 14
 * @author 2003
 */
public class ContentEntryEditorListenerAdapter implements ContentEntryEditor.ContentEntryEditorListener{
  @Override
  public void editingStarted(ContentEntryEditor editor) {
  }

  @Override
  public void beforeEntryDeleted(ContentEntryEditor editor) {
  }

  @Override
  public void sourceFolderAdded(ContentEntryEditor editor, SourceFolder folder) {
  }

  @Override
  public void sourceFolderRemoved(ContentEntryEditor editor, VirtualFile file, boolean isTestSource) {
  }

  @Override
  public void folderExcluded(ContentEntryEditor editor, VirtualFile file) {
  }

  @Override
  public void folderIncluded(ContentEntryEditor editor, VirtualFile file) {
  }

  @Override
  public void navigationRequested(ContentEntryEditor editor, VirtualFile file) {
  }

  @Override
  public void packagePrefixSet(ContentEntryEditor editor, SourceFolder folder) {
  }
}
