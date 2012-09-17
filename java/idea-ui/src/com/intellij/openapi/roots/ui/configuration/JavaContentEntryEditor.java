/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class JavaContentEntryEditor extends ContentEntryEditor {
  private final CompilerModuleExtension myCompilerExtension;

  public JavaContentEntryEditor(final String contentEntryUrl) {
    super(contentEntryUrl, true, true);
    myCompilerExtension = getModel().getModuleExtension(CompilerModuleExtension.class);
  }

  @Override
  protected ContentRootPanel createContentRootPane() {
    return new JavaContentRootPanel(this) {
      @Nullable
      @Override
      protected ContentEntry getContentEntry() {
        return JavaContentEntryEditor.this.getContentEntry();
      }
    };
  }

  @Override
  protected ExcludeFolder doAddExcludeFolder(@NotNull final VirtualFile file) {
    final boolean isCompilerOutput = isCompilerOutput(file);
    if (isCompilerOutput) {
      myCompilerExtension.setExcludeOutput(true);
      return null;
    }
    return super.doAddExcludeFolder(file);
  }

  @Override
  protected void doRemoveExcludeFolder(@NotNull final ExcludeFolder excludeFolder) {
    final VirtualFile file = excludeFolder.getFile();
    if (file != null) {
      if (isCompilerOutput(file)) {
        myCompilerExtension.setExcludeOutput(false);
      }
    }
    super.doRemoveExcludeFolder(excludeFolder);
  }

  private boolean isCompilerOutput(@NotNull final VirtualFile file) {
    final VirtualFile compilerOutputPath = myCompilerExtension.getCompilerOutputPath();
    if (file.equals(compilerOutputPath)) {
      return true;
    }

    final VirtualFile compilerOutputPathForTests = myCompilerExtension.getCompilerOutputPathForTests();
    if (file.equals(compilerOutputPathForTests)) {
      return true;
    }

    final String path = file.getPath();
    if (myCompilerExtension.isCompilerOutputPathInherited()) {
      final ProjectStructureConfigurable instance = ProjectStructureConfigurable.getInstance(getModel().getModule().getProject());
      final String compilerOutput = VfsUtil.urlToPath(instance.getProjectConfig().getCompilerOutputUrl());
      if (FileUtil.pathsEqual(compilerOutput, path)) {
        return true;
      }
    }

    return false;
  }
}
