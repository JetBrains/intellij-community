// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.reference;

import com.intellij.ide.projectView.impl.ProjectRootsUtil;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public final class RefDirectoryImpl extends RefElementImpl implements RefDirectory{
  private volatile RefModule myRefModule; // it's guaranteed that getModule() used after initialize()
  RefDirectoryImpl(PsiDirectory psiElement, RefManager refManager) {
    super(psiElement.getName(), psiElement, refManager);
  }

  @Override
  protected synchronized void initialize() {
    PsiDirectory psiElement = ObjectUtils.tryCast(getPsiElement(), PsiDirectory.class);
    LOG.assertTrue(psiElement != null);
    if (!ProjectRootsUtil.isSourceRoot(psiElement)) {
      final PsiDirectory parentDirectory = psiElement.getParentDirectory();
      if (parentDirectory != null && ProjectFileIndex.getInstance(psiElement.getProject()).isInSourceContent(parentDirectory.getVirtualFile())) {
        final WritableRefElement refElement = (WritableRefElement)getRefManager().getReference(parentDirectory);
        if (refElement != null) {
          refElement.add(this);
          return;
        }
      }
    }
    myRefModule = getRefManager().getRefModule(ModuleUtilCore.findModuleForPsiElement(psiElement));
    if (myRefModule != null) {
      ((WritableRefEntity)myRefModule).add(this);
      return;
    }
    ((WritableRefEntity)myManager.getRefProject()).add(this);
  }

  @Override
  public void accept(final @NotNull RefVisitor visitor) {
    ApplicationManager.getApplication().runReadAction(() -> visitor.visitDirectory(this));
  }

  @Override
  public boolean isValid() {
    if (isDeleted()) return false;
    return ReadAction.compute(() -> {
      if (getRefManager().getProject().isDisposed()) return false;

      VirtualFile directory = getVirtualFile();
      return directory != null && directory.isValid();
    });
  }

  @Override
  public @Nullable RefModule getModule() {
    return myRefModule != null ? myRefModule : super.getModule();
  }


  @Override
  public @NotNull String getQualifiedName() {
    return getName(); //todo relative name
  }

  @Override
  public String getExternalName() {
    final PsiElement element = getPsiElement();
    assert element != null;
    return ((PsiDirectory)element).getVirtualFile().getPath();
  }
}
