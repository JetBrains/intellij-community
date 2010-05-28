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

package com.intellij.ide;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiManager;
import com.intellij.refactoring.copy.CopyFilesOrDirectoriesHandler;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author yole
 */
public class FileListPasteProvider implements PasteProvider {
  public void performPaste(DataContext dataContext) {
    Project project = LangDataKeys.PROJECT.getData(dataContext);
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    if (project == null || ideView == null) return;
    List<File> fileList;
    try {
      final Transferable contents = CopyPasteManager.getInstance().getContents();
      //noinspection unchecked
      fileList = (List<File>)contents.getTransferData(DataFlavor.javaFileListFlavor);
    }
    catch (UnsupportedFlavorException e) {
      return;
    }
    catch (IOException e) {
      return;
    }
    if (fileList == null) return;
    List<PsiElement> elements = new ArrayList<PsiElement>();
    for (File file : fileList) {
      final VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
      if (vFile != null) {
        final PsiManager instance = PsiManager.getInstance(project);
        PsiFileSystemItem item = vFile.isDirectory() ? instance.findDirectory(vFile) : instance.findFile(vFile);
        if (item != null) {
          elements.add(item);
        }
      }
    }
    if (elements.size() > 0) {
      final PsiDirectory dir = ideView.getOrChooseDirectory();
      if (dir != null) {
        new CopyFilesOrDirectoriesHandler().doCopy(elements.toArray(new PsiElement[elements.size()]), dir);
      }
    }
  }

  public boolean isPastePossible(DataContext dataContext) {
    return true;
  }

  public boolean isPasteEnabled(DataContext dataContext) {
    final Transferable contents = CopyPasteManager.getInstance().getContents();
    final IdeView ideView = LangDataKeys.IDE_VIEW.getData(dataContext);
    return contents != null && contents.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && ideView != null;
  }
}
