// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.ide.projectView.impl.nodes.NamedLibraryElement;
import com.intellij.openapi.actionSystem.DataMap;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageDataUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.util.containers.ContainerUtil;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PROJECT_CONTEXT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_ELEMENT_ARRAY;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.PSI_FILE;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.SELECTED_ITEMS;
import static com.intellij.openapi.actionSystem.PlatformCoreDataKeys.VIRTUAL_FILE;
import static com.intellij.usages.UsageView.USAGES_KEY;
import static com.intellij.usages.UsageView.USAGE_TARGETS_KEY;

final class VirtualFileArrayRule {

  private static final Logger LOG = Logger.getInstance(VirtualFileArrayRule.class);

  static VirtualFile @Nullable [] getData(@NotNull DataMap dataProvider) {
    Set<VirtualFile> result = null;

    FileSystemTree fileSystemTree = dataProvider.get(FileSystemTree.DATA_KEY);
    if (fileSystemTree != null) {
      LOG.error("VirtualFileArrayRule must not be called when FileSystemTree.DATA_KEY data is present." +
                "FileSystemTree.DATA_KEY data provider must also provide FileSystemTree#getSelectedFiles() as VIRTUAL_FILE_ARRAY");
      return null;
    }
    else {
      Project project = dataProvider.get(PROJECT_CONTEXT);
      if (project != null && !project.isDisposed()) {
        result = addFiles(null, ProjectRootManager.getInstance(project).getContentRoots());
      }

      Module[] selectedModules = dataProvider.get(LangDataKeys.MODULE_CONTEXT_ARRAY);
      if (selectedModules != null) {
        for (Module selectedModule : selectedModules) {
          result = addFiles(result, ModuleRootManager.getInstance(selectedModule).getContentRoots());
        }
      }

      Module selectedModule = dataProvider.get(LangDataKeys.MODULE_CONTEXT);
      if (selectedModule != null && !selectedModule.isDisposed()) {
        result = addFiles(result, ModuleRootManager.getInstance(selectedModule).getContentRoots());
      }
    }

    PsiElement[] psiElements = dataProvider.get(PSI_ELEMENT_ARRAY);
    if (psiElements != null) {
      for (PsiElement element : psiElements) {
        result = addFilesFromPsiElement(result, element);
      }
    }

    result = addFile(result, dataProvider.get(VIRTUAL_FILE));

    PsiFile psiFile = dataProvider.get(PSI_FILE);
    if (psiFile != null) {
      result = addFile(result, psiFile.getVirtualFile());
    }

    if (result != null) {
      return VfsUtilCore.toVirtualFileArray(result);
    }

    PsiElement elem = dataProvider.get(PSI_ELEMENT);
    if (elem != null) {
      result = addFilesFromPsiElement(null, elem);
    }

    Usage[] usages = dataProvider.get(USAGES_KEY);
    UsageTarget[] usageTargets = dataProvider.get(USAGE_TARGETS_KEY);
    if (usages != null || usageTargets != null) {
      for (VirtualFile file : Objects.requireNonNull(UsageDataUtil.provideVirtualFileArray(usages, usageTargets))) {
        result = addFile(result, file);
      }
    }

    if (result == null) {
      Object[] objects = dataProvider.get(SELECTED_ITEMS);
      if (objects != null && objects.length != 0) {
        Object[] unwrapped = ContainerUtil.map2Array(objects, o -> AbstractProjectViewPane.extractValueFromNode(o));
        VirtualFile[] virtualFiles = null;
        if (ContainerUtil.all(unwrapped, o -> o instanceof VirtualFile)) {
          virtualFiles = Arrays.copyOf(unwrapped, unwrapped.length, VirtualFile[].class);
        }
        else if (ContainerUtil.all(unwrapped, o -> o instanceof SyntheticLibrary)) {
          virtualFiles = StreamEx.of(unwrapped).flatCollection(f -> ((SyntheticLibrary)f).getSourceRoots())
            .toArray(VirtualFile.EMPTY_ARRAY);
        }
        else if (ContainerUtil.all(unwrapped, o -> o instanceof NamedLibraryElement)) {
          virtualFiles = StreamEx.of(unwrapped).flatArray(f -> ((NamedLibraryElement)f).getOrderEntry().getRootFiles(OrderRootType.SOURCES))
            .toArray(VirtualFile.EMPTY_ARRAY);
        }
        return virtualFiles != null && virtualFiles.length != 0 ? virtualFiles : null;
      }
      return null;
    }
    else {
      return VfsUtilCore.toVirtualFileArray(result);
    }
  }

  private static @Nullable Set<VirtualFile> addFiles(@Nullable Set<VirtualFile> set, VirtualFile[] files) {
    for (VirtualFile file : files) {
      set = addFile(set, file);
    }
    return set;
  }

  private static @Nullable Set<VirtualFile> addFile(@Nullable Set<VirtualFile> set, @Nullable VirtualFile file) {
    if (file == null) return set;
    if (set == null) set = new LinkedHashSet<>();
    set.add(file);
    return set;
  }


  private static Set<VirtualFile> addFilesFromPsiElement(Set<VirtualFile> files, @NotNull PsiElement elem) {
    if (elem instanceof PsiDirectory) {
      files = addFile(files, ((PsiDirectory)elem).getVirtualFile());
    }
    else if (elem instanceof PsiFile) {
      files = addFile(files, ((PsiFile)elem).getVirtualFile());
    }
    else if (elem instanceof PsiDirectoryContainer) {
      for (PsiDirectory dir : ((PsiDirectoryContainer)elem).getDirectories()) {
        files = addFile(files, dir.getVirtualFile());
      }
    }
    else {
      PsiFile file = elem.getContainingFile();
      if (file != null) {
        files = addFile(files, file.getVirtualFile());
      }
    }
    return files;
  }

}