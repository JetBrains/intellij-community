// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ide.impl.dataRules;

import com.intellij.ide.projectView.impl.AbstractProjectViewPane;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataProvider;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileChooser.FileSystemTree;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiDirectoryContainer;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.usages.Usage;
import com.intellij.usages.UsageDataUtil;
import com.intellij.usages.UsageTarget;
import com.intellij.usages.UsageView;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

@ApiStatus.Internal
public final class VirtualFileArrayRule implements GetDataRule {

  private static final Logger LOG = Logger.getInstance(VirtualFileArrayRule.class);

  @Override
  public Object getData(@NotNull DataProvider dataProvider) {
    Set<VirtualFile> result = null;

    FileSystemTree fileSystemTree = FileSystemTree.DATA_KEY.getData(dataProvider);
    if (fileSystemTree != null) {
      LOG.error("VirtualFileArrayRule must not be called when FileSystemTree.DATA_KEY data is present." +
                "FileSystemTree.DATA_KEY data provider must also provide FileSystemTree#getSelectedFiles() as VIRTUAL_FILE_ARRAY");
      return null;
    }
    else {
      Project project = PlatformCoreDataKeys.PROJECT_CONTEXT.getData(dataProvider);
      if (project != null && !project.isDisposed()) {
        result = addFiles(null, ProjectRootManager.getInstance(project).getContentRoots());
      }

      Module[] selectedModules = LangDataKeys.MODULE_CONTEXT_ARRAY.getData(dataProvider);
      if (selectedModules != null) {
        for (Module selectedModule : selectedModules) {
          result = addFiles(result, ModuleRootManager.getInstance(selectedModule).getContentRoots());
        }
      }

      Module selectedModule = LangDataKeys.MODULE_CONTEXT.getData(dataProvider);
      if (selectedModule != null && !selectedModule.isDisposed()) {
        result = addFiles(result, ModuleRootManager.getInstance(selectedModule).getContentRoots());
      }
    }

    PsiElement[] psiElements = PlatformCoreDataKeys.PSI_ELEMENT_ARRAY.getData(dataProvider);
    if (psiElements != null) {
      for (PsiElement element : psiElements) {
        result = addFilesFromPsiElement(result, element);
      }
    }

    result = addFile(result, CommonDataKeys.VIRTUAL_FILE.getData(dataProvider));

    PsiFile psiFile = CommonDataKeys.PSI_FILE.getData(dataProvider);
    if (psiFile != null) {
      result = addFile(result, psiFile.getVirtualFile());
    }

    if (result != null) {
      return VfsUtilCore.toVirtualFileArray(result);
    }

    PsiElement elem = CommonDataKeys.PSI_ELEMENT.getData(dataProvider);
    if (elem != null) {
      result = addFilesFromPsiElement(null, elem);
    }

    Usage[] usages = UsageView.USAGES_KEY.getData(dataProvider);
    UsageTarget[] usageTargets = UsageView.USAGE_TARGETS_KEY.getData(dataProvider);
    if (usages != null || usageTargets != null) {
      for (VirtualFile file : Objects.requireNonNull(UsageDataUtil.provideVirtualFileArray(usages, usageTargets))) {
        result = addFile(result, file);
      }
    }

    if (result == null) {
      Object[] objects = (Object[])dataProvider.getData(PlatformCoreDataKeys.SELECTED_ITEMS.getName());
      if (objects != null && objects.length != 0) {
        Object[] unwrapped = ContainerUtil.map2Array(objects, o -> AbstractProjectViewPane.extractValueFromNode(o));
        if (ContainerUtil.all(unwrapped, o -> o instanceof VirtualFile)) {
          return Arrays.copyOf(unwrapped, unwrapped.length, VirtualFile[].class);
        }
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