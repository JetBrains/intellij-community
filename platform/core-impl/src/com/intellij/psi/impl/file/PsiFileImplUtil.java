// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.impl.file;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.fileTypes.UnknownFileType;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.function.Consumer;

public final class PsiFileImplUtil {
  private static final Key<Consumer<PsiFile>> NON_PHYSICAL_FILE_DELETE_HANDLER = Key.create("NonPhysicalFileDeleteHandler");
  
  private PsiFileImplUtil() {
  }

  // before the file becomes non-openable in the editor, save it to prevent data loss
  @ApiStatus.Internal
  public static void saveDocumentIfFileWillBecomeBinary(VirtualFile vFile, @NotNull String newName) {
    FileType newFileType = FileTypeRegistry.getInstance().getFileTypeByFileName(newName);
    if (UnknownFileType.INSTANCE.equals(newFileType) || newFileType.isBinary()) {
      FileDocumentManager fdm = FileDocumentManager.getInstance();
      Document doc = fdm.getCachedDocument(vFile);
      if (doc != null) {
        fdm.saveDocumentAsIs(doc);
      }
    }
  }

  @ApiStatus.Experimental
  public static void setNonPhysicalFileDeleteHandler(@NotNull PsiFile file, @NotNull Consumer<@NotNull PsiFile> handler) {
    if (file.isPhysical()) {
      throw new IllegalArgumentException();
    }
    file.putUserData(NON_PHYSICAL_FILE_DELETE_HANDLER, handler);
  }

  public static boolean canDeleteNonPhysicalFile(@NotNull PsiFile file) {
    return !file.isPhysical() && file.getUserData(NON_PHYSICAL_FILE_DELETE_HANDLER) != null;
  }

  public static PsiFile setName(@NotNull PsiFile file, @NotNull String newName) throws IncorrectOperationException {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    PsiManager manager = file.getManager();

    try{
      saveDocumentIfFileWillBecomeBinary(vFile, newName);

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
    if (!file.isPhysical()) {
      Consumer<PsiFile> handler = file.getUserData(NON_PHYSICAL_FILE_DELETE_HANDLER);
      if (handler == null) {
        throw new IncorrectOperationException();
      }
      handler.accept(file);
      return;
    }
    
    PsiManager manager = file.getManager();

    VirtualFile vFile = file.getVirtualFile();
    try{
      vFile.delete(manager);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e);
    }
  }
}
