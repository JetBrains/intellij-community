// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.psi.impl.file;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public final class PsiFileImplUtil {
  private PsiFileImplUtil() {
  }

  public static PsiFile setName(@NotNull PsiFile file, @NotNull String newName) throws IncorrectOperationException {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    try{
      final FileType newFileType = FileTypeRegistry.getInstance().getFileTypeByFileName(newName);
      if (UnknownFileType.INSTANCE.equals(newFileType) || newFileType.isBinary()) {
        // before the file becomes unknown or a binary (thus, not openable in the editor), save it to prevent data loss
        final FileDocumentManager fdm = FileDocumentManager.getInstance();
        final Document doc = fdm.getCachedDocument(vFile);
        if (doc != null) {
          fdm.saveDocumentAsIs(doc);
        }
      }

      vFile.rename(manager, newName);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e);
    }

    return file.getViewProvider().isPhysical() ? manager.findFile(vFile) : file;
  }

  public static void checkSetName(@NotNull PsiFile file, @NotNull String name) throws IncorrectOperationException {
    VirtualFile vFile = file.getVirtualFile();
    VirtualFile parentFile = vFile.getParent();
    if (parentFile == null) return;
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(vFile)){
      throw new IncorrectOperationException("File " + child.getPresentableUrl() + " already exists.");
    }
  }

  public static void doDelete(@NotNull PsiFile file) throws IncorrectOperationException {
    final PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    final VirtualFile vFile = file.getVirtualFile();
    try{
      vFile.delete(manager);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e);
    }
  }
}
