// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.ide.dnd.LinuxDragAndDropSupport;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesHandler;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.ArrayList;
import java.util.List;


@ApiStatus.Internal
public final class FileListPasteProvider implements PasteProvider {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void performPaste(@NotNull DataContext dataContext) {
    final Project project = CommonDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;

    if (!FileCopyPasteUtil.isFileListFlavorAvailable()) return;

    final Transferable contents = CopyPasteManager.getInstance().getContents();
    if (contents == null) return;
    final List<File> fileList = FileCopyPasteUtil.getFileList(contents);
    if (fileList == null) return;

    final List<PsiElement> elements = new ArrayList<>();
    for (File file : fileList) {
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      PsiFileSystemItem item = PsiUtilCore.findFileSystemItem(project, vFile);
      if (item != null) {
        elements.add(item);
      }
    }

    if (elements.isEmpty()) {
      return;
    }
    final PsiDirectory dir = ideView.getOrChooseDirectory();
    if (dir != null) {
      final boolean move = LinuxDragAndDropSupport.isMoveOperation(contents);
      if (move) {
        if (DumbService.isDumb(project) &&
            Messages.showYesNoDialog(project, RefactoringBundle.message("move.handler.is.dumb.during.indexing"),
                                     RefactoringBundle.message("move.title"), Messages.getQuestionIcon()) != Messages.YES) {
          return;
        }
        new MoveFilesOrDirectoriesHandler().doMove(PsiUtilCore.toPsiElementArray(elements), dir);
      }
      else {
        new CopyFilesOrDirectoriesHandler().doCopy(PsiUtilCore.toPsiElementArray(elements), dir);
      }
    }
  }

  @Override
  public boolean isPastePossible(@NotNull DataContext dataContext) {
    return true;
  }

  @Override
  public boolean isPasteEnabled(@NotNull DataContext dataContext) {
    return LangDataKeys.IDE_VIEW.getData(dataContext) != null &&
           FileCopyPasteUtil.isFileListFlavorAvailable();
  }
}
