/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.projectView.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
public class MarkExcludeRootAction extends MarkRootActionBase {
  @Override
  public void actionPerformed(AnActionEvent e) {
    VirtualFile[] files = e.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY);

    if (Registry.is("ide.hide.excluded.files")) {
      String message = files.length == 1 ? FileUtil.toSystemDependentName(files[0].getPath()) : files.length + " selected files";
      final int rc = Messages.showOkCancelDialog(e.getData(CommonDataKeys.PROJECT), getPromptText(message), "Mark as Excluded",
                                                 Messages.getQuestionIcon());
      if (rc != Messages.OK) {
        return;
      }
    }
    super.actionPerformed(e);
  }

  protected String getPromptText(String message) {
    return "Are you sure you would like to exclude " + message +
           " from the project?\nYou can restore excluded directories later using the Project Structure dialog.";
  }

  protected void modifyRoots(@NotNull VirtualFile vFile, @NotNull ContentEntry entry) {
    entry.addExcludeFolder(vFile);
  }

  @Override
  protected boolean isEnabled(@NotNull RootsSelection selection, @NotNull Module module) {
    return true;
  }
}
