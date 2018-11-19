/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.project.ProjectKt;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

/**
 * @author Nikolai Matveev
 */
public abstract class AbstractConvertLineSeparatorsAction extends AnAction {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeStyle.AbstractConvertLineSeparatorsAction");

  @NotNull
  private final String mySeparator;

  protected AbstractConvertLineSeparatorsAction(@Nullable String text, @NotNull LineSeparator separator) {
    this(separator + " - " + text, separator.getSeparatorString());
  }

  protected AbstractConvertLineSeparatorsAction(@Nullable String text, @NotNull String separator) {
    super(text);
    mySeparator = separator;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DataContext dataContext = e.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project != null) {
      final VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
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
  public void actionPerformed(@NotNull AnActionEvent event) {
    final DataContext dataContext = event.getDataContext();
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    if (project == null) {
      return;
    }

    final VirtualFile[] virtualFiles = CommonDataKeys.VIRTUAL_FILE_ARRAY.getData(dataContext);
    if (virtualFiles == null) {
      return;
    }

    VirtualFile projectVirtualDirectory = ProjectKt.getStateStore(project).getDirectoryStoreFile();
    final FileTypeRegistry fileTypeManager = FileTypeRegistry.getInstance();
    for (VirtualFile file : virtualFiles) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor() {
        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          if (shouldProcess(file, project)) {
            changeLineSeparators(project, file, mySeparator);
          }
          return file.isDirectory() && (file.equals(projectVirtualDirectory) || fileTypeManager.isFileIgnored(file)) ? SKIP_CHILDREN : CONTINUE;
        }
      });
    }
  }

  public static boolean shouldProcess(@NotNull VirtualFile file, @NotNull Project project) {
    return !(file.isDirectory()
             || !file.isWritable()
             || file instanceof VirtualFileWindow
             || FileTypeRegistry.getInstance().isFileIgnored(file)
             || file.getFileType().isBinary()
             || file.equals(project.getProjectFile())
             || file.equals(project.getWorkspaceFile()));
  }

  public static void changeLineSeparators(@NotNull final Project project,
                                          @NotNull final VirtualFile virtualFile,
                                          @NotNull final String newSeparator) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getCachedDocument(virtualFile);
    if (document != null) {
      fileDocumentManager.saveDocument(document);
    }

    String currentSeparator = LoadTextUtil.detectLineSeparator(virtualFile, false);
    final String commandText;
    if (StringUtil.isEmpty(currentSeparator)) {
      commandText = "Changed line separators to " + LineSeparator.fromString(newSeparator);
    }
    else {
      commandText = String.format("Changed line separators from %s to %s",
                                  LineSeparator.fromString(currentSeparator), LineSeparator.fromString(newSeparator));
    }

    WriteCommandAction.writeCommandAction(project).withName(commandText).run(() -> {
      try {
        LoadTextUtil.changeLineSeparators(project, virtualFile, newSeparator, virtualFile);
      }
      catch (IOException e) {
        LOG.info(e);
      }
    });
  }
}
