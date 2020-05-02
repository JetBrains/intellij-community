// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.lightEdit;

import com.intellij.ide.dnd.FileCopyPasteUtil;
import com.intellij.openapi.editor.EditorDropHandler;
import com.intellij.openapi.fileEditor.impl.EditorWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.ObjectUtils;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.util.List;

import static com.intellij.ide.lightEdit.LightEditFeatureUsagesUtil.OpenPlace.DragAndDrop;

class LightEditDropHandler implements EditorDropHandler {
  @Override
  public boolean canHandleDrop(DataFlavor[] transferFlavors) {
    return transferFlavors != null && FileCopyPasteUtil.isFileListFlavorAvailable(transferFlavors);
  }

  @Override
  public void handleDrop(Transferable t,
                         Project project,
                         EditorWindow editorWindow) {
    final List<File> fileList = FileCopyPasteUtil.getFileList(t);
    if (fileList != null) {
      fileList.forEach(file -> {
        ObjectUtils.doIfNotNull(VfsUtil.findFileByIoFile(file, true), virtualFile -> {
          if (LightEditService.getInstance().openFile(virtualFile))
            LightEditFeatureUsagesUtil.logFileOpen(DragAndDrop);
          return true;
        });
      });
    }
  }
}
