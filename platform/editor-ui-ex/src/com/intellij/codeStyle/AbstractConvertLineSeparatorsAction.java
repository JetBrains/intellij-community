// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeStyle;

import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.PlatformEditorBundle;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.InternalFileType;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsActions;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.project.ProjectKt;
import com.intellij.util.LineSeparator;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * @author Nikolai Matveev
 */
public abstract class AbstractConvertLineSeparatorsAction extends AnAction implements DumbAware, LightEditCompatible {
  private static final Logger LOG = Logger.getInstance(AbstractConvertLineSeparatorsAction.class);

  @NotNull
  private final String mySeparator;

  protected AbstractConvertLineSeparatorsAction(@NotNull Supplier<@NlsActions.ActionText String> text, @NotNull LineSeparator separator) {
    this(separator + " - " + text.get(), separator.getSeparatorString());
  }

  protected AbstractConvertLineSeparatorsAction(@Nullable @NlsActions.ActionText String text, @NotNull String separator) {
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

    Path directoryStorePath = ProjectKt.getStateStore(project).getDirectoryStorePath();
    VirtualFile projectVirtualDirectory = directoryStorePath == null ? null : StandardFileSystems.local().findFileByPath(FileUtil.toSystemIndependentName(directoryStorePath.toString()));
    FileTypeRegistry fileTypeManager = FileTypeRegistry.getInstance();
    for (VirtualFile file : virtualFiles) {
      VfsUtilCore.visitChildrenRecursively(file, new VirtualFileVisitor<Void>() {
        @NotNull
        @Override
        public Result visitFileEx(@NotNull VirtualFile file) {
          if (shouldProcess(file)) {
            changeLineSeparators(project, file, mySeparator);
          }
          return file.isDirectory() && (file.equals(projectVirtualDirectory) || fileTypeManager.isFileIgnored(file)) ? SKIP_CHILDREN : CONTINUE;
        }
      });
    }
  }

  public static boolean shouldProcess(@NotNull VirtualFile file) {
    return !(file.isDirectory()
             || !file.isWritable()
             || file instanceof VirtualFileWindow
             || FileTypeRegistry.getInstance().isFileIgnored(file)
             || file.getFileType().isBinary()
             || file.getFileType() instanceof InternalFileType);
  }

  public static void changeLineSeparators(@NotNull Project project,
                                          @NotNull VirtualFile virtualFile,
                                          @NotNull String newSeparator) {
    FileDocumentManager fileDocumentManager = FileDocumentManager.getInstance();
    Document document = fileDocumentManager.getCachedDocument(virtualFile);
    if (document != null) {
      fileDocumentManager.saveDocument(document);
    }

    String currentSeparator = LoadTextUtil.detectLineSeparator(virtualFile, false);
    final String commandText;
    if (StringUtil.isEmpty(currentSeparator)) {
      commandText = PlatformEditorBundle.message("command.name.changed.line.separators.to", LineSeparator.fromString(newSeparator));
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
