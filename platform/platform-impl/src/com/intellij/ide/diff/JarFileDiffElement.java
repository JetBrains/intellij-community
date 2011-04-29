/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.ide.diff;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Konstantin Bulenkov
 */
public class JarFileDiffElement extends VirtualFileDiffElement {
  @SuppressWarnings({"ConstantConditions"})
  public JarFileDiffElement(@NotNull VirtualFile file) {
    super(file.getFileSystem() == JarFileSystem.getInstance()
          ? file : JarFileSystem.getInstance().getJarRootForLocalFile(file));
  }

  protected VirtualFileDiffElement createElement(VirtualFile file) {
    final VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    return jar == null ? null : new JarFileDiffElement(file);
  }

  protected FileChooserDescriptor getChooserDescriptor() {
    return new FileChooserDescriptor(true, false, true, true, false, false) {
      @Override
      public boolean isFileVisible(VirtualFile file, boolean showHiddenFiles) {
        return file.isDirectory()
               || (!file.isDirectory() && JarFileSystem.PROTOCOL.equalsIgnoreCase(file.getExtension()));
      }
    };
  }
}
