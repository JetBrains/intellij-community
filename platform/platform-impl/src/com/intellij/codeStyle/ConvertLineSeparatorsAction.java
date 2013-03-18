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
package com.intellij.codeStyle;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Nikolai Matveev
 */
public abstract class ConvertLineSeparatorsAction extends AnAction {

  private static Logger LOG = Logger.getInstance("#com.strintec.intellij.webmaster.lineSeparator.ConvertLineSeparatorsAction");

  @NotNull
  private final String mySeparator;

  protected ConvertLineSeparatorsAction(@Nullable String text, @NotNull LineSeparator separator) {
    this(text, separator.getSeparatorString());
  }
  
  protected ConvertLineSeparatorsAction(@Nullable String text, @NotNull String separator) {
    super(text);
    mySeparator = separator;
  }

  @Override
  public void update(AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      final Presentation presentation = e.getPresentation();
      if (virtualFiles != null) {
        if (virtualFiles.length == 1) {
          presentation.setEnabled(!mySeparator.equals(LoadTextUtil.detectLineSeparator(virtualFiles[0], false)));
        }
        else {
          presentation.setEnabled(true);
        }
      }
      else {
        presentation.setEnabled(false);
      }
    }
  }


  @Override
  public void actionPerformed(AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = PlatformDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final VirtualFile[] virtualFiles = PlatformDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
      if (virtualFiles != null) {
        final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();
        for (VirtualFile virtualFile : virtualFiles) {
          fileIndex.iterateContentUnderDirectory(virtualFile, new ContentIterator() {
            @Override
            public boolean processFile(VirtualFile fileOrDir) {
              if (!fileOrDir.isDirectory()) {
                changeLineSeparators(project, fileOrDir);
              }
              return true;
            }
          });
        }
      }
    }
  }

  private void changeLineSeparators(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    try {
      CharSequence currentText = LoadTextUtil.getTextByBinaryPresentation(virtualFile.contentsToByteArray(), virtualFile);
      String currentLineSeparator = LoadTextUtil.detectLineSeparator(virtualFile, false);
      if (mySeparator.equals(currentLineSeparator)) {
        return;
      }
      String newText = StringUtil.convertLineSeparators(currentText.toString(), mySeparator);
      LoadTextUtil.write(project, virtualFile, this, newText, -1);
    }
    catch (IOException e) {
      LOG.warn(e);
    }
  }

}
