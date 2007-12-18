package com.intellij.psi.impl.file;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.util.IncorrectOperationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PsiFileImplUtil {
  private static final Logger LOG = Logger.getInstance("#com.intellij.psi.impl.file.PsiFileImplUtil");

  public static PsiFile setName(final PsiFile file, String newName) throws IncorrectOperationException {
    VirtualFile vFile = file.getViewProvider().getVirtualFile();
    PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    try{
      vFile.rename(manager, newName);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e.toString(),e);
    }

    return file.getViewProvider().isPhysical() ? manager.findFile(vFile) : file;
  }

  public static void checkSetName(PsiFile file, String name) throws IncorrectOperationException {
    VirtualFile vFile = file.getVirtualFile();
    VirtualFile parentFile = vFile.getParent();
    if (parentFile == null) return;
    VirtualFile child = parentFile.findChild(name);
    if (child != null && !child.equals(vFile)){
      throw new IncorrectOperationException("File " + child.getPresentableUrl() + " already exists.");
    }
  }

  public static void doDelete(final PsiFile file) throws IncorrectOperationException {
    final PsiManagerImpl manager = (PsiManagerImpl)file.getManager();

    final VirtualFile vFile = file.getVirtualFile();
    try{
      vFile.delete(manager);
    }
    catch(IOException e){
      throw new IncorrectOperationException(e.toString(),e);
    }
  }

  public static PsiFile[] getPsiFilesByVirtualFiles(VirtualFile[] files, PsiManager manager) {
    List<PsiFile> psiFiles = new ArrayList<PsiFile>();
    for (VirtualFile file : files) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return psiFiles.toArray(new PsiFile[psiFiles.size()]);
  }

  public static PsiFile[] getPsiFilesByVirtualFiles(List<VirtualFile> files, PsiManager manager) {
    List<PsiFile> psiFiles = new ArrayList<PsiFile>();

    for (VirtualFile file : files) {
      PsiFile psiFile = manager.findFile(file);
      if (psiFile != null) {
        psiFiles.add(psiFile);
      }
    }
    return psiFiles.toArray(new PsiFile[psiFiles.size()]);
  }
}
