// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.reference;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class RefFileImpl extends RefElementImpl implements RefFile {
  private final RefModule myRefModule;

  public RefFileImpl(PsiFile elem, RefManager manager) {
    super(elem, manager);
    myRefModule = getRefManager().getRefModule(ModuleUtilCore.findModuleForFile(getPsiElement()));
  }

  @Override
  public PsiFile getPsiElement() {
    return (PsiFile)super.getPsiElement();
  }

  @Override
  public void accept(@NotNull final RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(() -> visitor.visitFile(this));
  }

  @Override
  public String getExternalName() {
    final PsiFile psiFile = getPsiElement();
    final VirtualFile virtualFile = psiFile != null ? psiFile.getVirtualFile() : null;
    return virtualFile != null ? virtualFile.getUrl() : getName();
  }

  @Override
  protected void initialize() {
    final VirtualFile vFile = getVirtualFile();
    if (vFile == null) return;
    final VirtualFile parentDirectory = vFile.getParent();
    if (parentDirectory == null) return;
    final PsiDirectory psiDirectory = getRefManager().getPsiManager().findDirectory(parentDirectory);
    if (psiDirectory != null) {
      final RefElement element = getRefManager().getReference(psiDirectory);
      if (element != null) {
        ((RefElementImpl)element).add(this);
      }
    }
  }

  @Nullable
  static RefElement fileFromExternalName(final RefManager manager, final String fqName) {
    final VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(PathMacroManager.getInstance(manager.getProject()).expandPath(fqName));
    if (virtualFile != null) {
      final PsiFile psiFile = PsiManager.getInstance(manager.getProject()).findFile(virtualFile);
      if (psiFile != null) {
        return manager.getReference(psiFile);
      }
    }
    return null;
  }

  @Override
  public RefModule getModule() {
    return myRefModule;
  }
}
