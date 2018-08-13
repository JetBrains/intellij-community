// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @Override
  protected VirtualFileDiffElement createElement(VirtualFile file) {
    final VirtualFile jar = JarFileSystem.getInstance().getJarRootForLocalFile(file);
    return jar == null ? null : new JarFileDiffElement(file);
  }

  @Override
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
