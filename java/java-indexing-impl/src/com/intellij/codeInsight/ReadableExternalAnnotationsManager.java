// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class ReadableExternalAnnotationsManager extends BaseExternalAnnotationsManager {
  @Nullable private Set<VirtualFile> myAnnotationsRoots;

  public ReadableExternalAnnotationsManager(PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean hasAnyAnnotationsRoots() {
    return !initRoots().isEmpty();
  }

  @NotNull
  private synchronized Set<VirtualFile> initRoots() {
    if (myAnnotationsRoots == null) {
      myAnnotationsRoots = new HashSet<>();
      final Module[] modules = ModuleManager.getInstance(myPsiManager.getProject()).getModules();
      for (Module module : modules) {
        for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
          final VirtualFile[] files = AnnotationOrderRootType.getFiles(entry);
          if (files.length > 0) {
            Collections.addAll(myAnnotationsRoots, files);
          }
        }
      }
    }
    return myAnnotationsRoots;
  }

  @Override
  @NotNull
  protected List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile) {
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
    Set<VirtualFile> result = new LinkedHashSet<>();
    for (OrderEntry entry : fileIndex.getOrderEntriesForFile(libraryFile)) {
      ProgressManager.checkCanceled();
      if (!(entry instanceof ModuleOrderEntry)) {
        Collections.addAll(result, AnnotationOrderRootType.getFiles(entry));
      }
    }
    ContainerUtil.addIfNotNull(result, getAdditionalAnnotationRoot());
    return new ArrayList<>(result);
  }

  protected @Nullable VirtualFile getAdditionalAnnotationRoot() {
    return null;
  }


  @Override
  protected synchronized void dropCache() {
    myAnnotationsRoots = null;
    super.dropCache();
  }

  public boolean isUnderAnnotationRoot(VirtualFile file) {
    return VfsUtilCore.isUnder(file, initRoots());
  }

  @Override
  public boolean hasConfiguredAnnotationRoot(@NotNull PsiModifierListOwner owner) {
    VirtualFile file = PsiUtilCore.getVirtualFile(owner);
    if (file == null) {
      return false;
    }
    final List<OrderEntry> entries = 
     ProjectRootManager.getInstance(owner.getProject())
       .getFileIndex()
       .getOrderEntriesForFile(file);

    return entries.stream().anyMatch(entry -> entry instanceof LibraryOrSdkOrderEntry &&
                                              ContainerUtil.filter(AnnotationOrderRootType.getFiles(entry), VirtualFile::isInLocalFileSystem).size() == 1);
 }
}
