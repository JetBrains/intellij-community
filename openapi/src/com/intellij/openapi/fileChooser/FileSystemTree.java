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
package com.intellij.openapi.fileChooser;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;

public interface FileSystemTree extends Disposable {
  JTree getTree();

  void updateTree();

  @Nullable
  VirtualFile getSelectedFile();

  /**
   * @deprecated
   */
  boolean select(VirtualFile file);

  void select(VirtualFile file, @Nullable Runnable onDone);

  void select(VirtualFile[] files, @Nullable Runnable onDone);


  /**
   * @deprecated
   */
  boolean expand(VirtualFile file);

  void expand(VirtualFile file, @Nullable Runnable onDone);

  void addListener(Listener listener, Disposable parent);

  boolean isUnderRoots(VirtualFile file);

  boolean selectionExists();

  VirtualFile[] getSelectedFiles();

  interface Listener {
    void selectionChanged(List<VirtualFile> selection);
  }
}
