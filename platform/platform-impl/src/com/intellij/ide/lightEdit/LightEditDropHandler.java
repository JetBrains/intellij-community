// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

final class LightEditDropHandler implements EditorDropHandler {
  @Override
  public boolean canHandleDrop(DataFlavor[] transferFlavors) {
    return transferFlavors != null && FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors);
  }

  @Override
  public void handleDrop(Transferable t, Project project, EditorWindow editorWindow) {
    List<File> fileList = FileCopyPasteUtil.getFileList(t);
    if (fileList == null) {
      return;
    }

    for (File file : fileList) {
      VirtualFile obj = VfsUtil.findFileByIoFile(file, true);
      if (obj != null) {
        LightEditService.getInstance().openFile(obj);
        LightEditFeatureUsagesUtil.logFileOpen(project, LightEditFeatureUsagesUtil.OpenPlace.DragAndDrop);
      }
    }
  }
}
