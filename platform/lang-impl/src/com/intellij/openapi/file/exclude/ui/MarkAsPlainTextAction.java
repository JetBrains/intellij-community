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
package com.intellij.openapi.file.exclude.ui;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeFactory;
import com.intellij.openapi.file.exclude.EnforcedPlainTextFileTypeManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

/**
 * @author Rustam Vishnyakov
 */
public class MarkAsPlainTextAction extends AnAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    if (file == null || file.isDirectory()) return;
    markAsPlainText(file);
  }

  private static void markAsPlainText(@NotNull VirtualFile file) {
    EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    if (typeManager != null) typeManager.markAsPlainText(file);
  }

  @Override
  public void update(AnActionEvent e) {
    DataContext dataContext = e.getDataContext();
    final VirtualFile file = PlatformDataKeys.VIRTUAL_FILE.getData(dataContext);
    final Presentation presentation = e.getPresentation();
    final EnforcedPlainTextFileTypeManager typeManager = EnforcedPlainTextFileTypeManager.getInstance();
    presentation.setVisible(false);
    if (typeManager == null || file == null) {
      return;
    }
    if (EnforcedPlainTextFileTypeManager.isApplicableFor(file) && !typeManager.isMarkedAsPlainText(file)) {
      presentation.setVisible(true);
      presentation.setIcon(EnforcedPlainTextFileTypeFactory.ENFORCED_PLAIN_TEXT_ICON);
    }
  }
    
}
