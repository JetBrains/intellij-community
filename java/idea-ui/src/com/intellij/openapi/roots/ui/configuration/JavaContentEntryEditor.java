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

import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ExcludeFolder;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

public abstract class JavaContentEntryEditor extends ContentEntryEditor {
  private final CompilerModuleExtension myCompilerExtension;

  public JavaContentEntryEditor(final String contentEntryUrl) {
    super(contentEntryUrl, true, true);
    myCompilerExtension = getModel().getModuleExtension(CompilerModuleExtension.class);
  }

  protected ContentRootPanel createContentRootPane() {
    return new JavaContentRootPanel(this) {
      @Nullable
      @Override
      protected ContentEntry getContentEntry() {
        return JavaContentEntryEditor.this.getContentEntry();
      }
    };
  }

  @Nullable
  @Override
  protected ExcludeFolder doAddExcludeFolder(VirtualFile file) {
    final boolean isCompilerOutput = isCompilerOutput(file);
    boolean isExplodedDirectory = isExplodedDirectory(file);
    if (isCompilerOutput || isExplodedDirectory) {
      if (isCompilerOutput) {
        myCompilerExtension.setExcludeOutput(true);
      }
      if (isExplodedDirectory) {
        getModel().setExcludeExplodedDirectory(true);
      }
      return null;
    }
    return super.doAddExcludeFolder(file);
  }

  @Override
  protected void doRemoveExcludeFolder(ExcludeFolder excludeFolder, VirtualFile file) {
    if (isCompilerOutput(file)) {
      myCompilerExtension.setExcludeOutput(false);
    }
    if (isExplodedDirectory(file)) {
      getModel().setExcludeExplodedDirectory(false);
    }
    super.doRemoveExcludeFolder(excludeFolder, file);
  }

  private boolean isCompilerOutput(@Nullable VirtualFile file) {
    final VirtualFile compilerOutputPath = myCompilerExtension.getCompilerOutputPath();
    if (compilerOutputPath != null) {
      if (compilerOutputPath.equals(file)) {
        return true;
      }
    }

    final VirtualFile compilerOutputPathForTests = myCompilerExtension.getCompilerOutputPathForTests();
    if (compilerOutputPathForTests != null) {
      if (compilerOutputPathForTests.equals(file)) {
        return true;
      }
    }

    if (myCompilerExtension.isCompilerOutputPathInherited()) {
      final String compilerOutput = ProjectStructureConfigurable.getInstance(getModel().getModule().getProject()).getProjectConfig().getCompilerOutputUrl();
      if (file != null && Comparing.equal(compilerOutput, file.getUrl())) {
        return true;
      }
    }

    return false;
  }

  private boolean isExplodedDirectory(VirtualFile file) {
    final VirtualFile explodedDir = getModel().getExplodedDirectory();
    if (explodedDir != null) {
      if (explodedDir.equals(file)) {
        return true;
      }
    }
    return false;
  }

}
