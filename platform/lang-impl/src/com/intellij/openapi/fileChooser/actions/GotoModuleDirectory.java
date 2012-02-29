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

package com.intellij.openapi.fileChooser.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.module.Module;
import com.intellij.util.PathUtil;
import org.jetbrains.annotations.Nullable;

public final class GotoModuleDirectory extends FileChooserAction {
  protected void actionPerformed(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final String path = getModulePath(e);
    if (path != null) {
      fileSystemTree.select(new Runnable() {
        public void run() {
          fileSystemTree.expand(path, null);
        }
      }, path);
    }
  }

  protected void update(final FileSystemTree fileSystemTree, final AnActionEvent e) {
    final Presentation presentation = e.getPresentation();
    final String path = getModulePath(e);
    presentation.setEnabled(path != null && fileSystemTree.isUnderRoots(path));
  }

  @Nullable
  private static String getModulePath(final AnActionEvent e) {
    Module module = e.getData(LangDataKeys.MODULE_CONTEXT);
    if (module == null) {
      module = e.getData(LangDataKeys.MODULE);
    }
    return module == null || module.isDisposed() ? null : PathUtil.getParentPath(module.getModuleFilePath());
  }
}
