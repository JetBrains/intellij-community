// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.roots.AnnotationOrderRootType;
import com.intellij.openapi.roots.JavaModuleExternalPaths;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.PersistentOrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.platform.backend.workspace.VirtualFileUrls;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.psi.util.PsiUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class ReadableExternalAnnotationsManager extends BaseExternalAnnotationsManager {
  private @Nullable Set<VirtualFile> myAnnotationsRoots;

  public ReadableExternalAnnotationsManager(PsiManager psiManager) {
    super(psiManager);
  }

  @Override
  protected boolean hasAnyAnnotationsRoots() {
    return !initRoots().isEmpty();
  }

  private synchronized @NotNull Set<VirtualFile> initRoots() {
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
  protected @NotNull List<VirtualFile> getExternalAnnotationsRoots(@NotNull VirtualFile libraryFile) {
    if (!hasAnyAnnotationsRoots()) {
      return Collections.emptyList();
    }
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(myPsiManager.getProject()).getFileIndex();
    Set<VirtualFile> result = new LinkedHashSet<>();
    var libraryRootType = LibraryBridgeImpl.Companion.toLibraryRootType(AnnotationOrderRootType.getInstance());
    var sdkRootName = ((PersistentOrderRootType) AnnotationOrderRootType.getInstance()).getSdkRootName();

    var module = fileIndex.getModuleForFile(libraryFile);

    if (module != null) {
      Collections.addAll(result, ModuleRootManager.getInstance(module).getModuleExtension(JavaModuleExternalPaths.class).getExternalAnnotationsRoots());
    }

    for (var library : fileIndex.findContainingLibraries(libraryFile)) {
      library.getRoots().stream()
        .filter((root) -> root.getType().equals(libraryRootType))
        .map((root) -> root.getUrl())
        .map((url) -> VirtualFileUrls.getVirtualFile(url))
        .filter(Objects::nonNull)
        .forEach(result::add);
    }

    for (var sdk : fileIndex.findContainingSdks(libraryFile)) {
      sdk.getRoots().stream()
        .filter((root) -> root.getType().getName().equals(sdkRootName))
        .map((root) -> root.getUrl())
        .map((url) -> VirtualFileUrls.getVirtualFile(url))
        .filter(Objects::nonNull)
        .forEach(result::add);
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

    return ContainerUtil.exists(entries, entry -> entry instanceof LibraryOrSdkOrderEntry &&
                                                  ContainerUtil.filter(AnnotationOrderRootType.getFiles(entry),
                                                                       VirtualFile::isInLocalFileSystem).size() == 1);
 }
}
