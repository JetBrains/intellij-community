// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.actions;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSet;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

final class FileRecursiveIterator {
  private final @NotNull Project myProject;
  private final @NotNull Collection<? extends VirtualFile> myRoots;

  FileRecursiveIterator(@NotNull Project project, @NotNull List<? extends PsiFile> roots) {
    this(project, ContainerUtil.<PsiFile, VirtualFile>map(roots, psiDir -> psiDir.getVirtualFile()));
  }

  FileRecursiveIterator(@NotNull Module module) {
    this(module.getProject(), ContainerUtil.<PsiDirectory, VirtualFile>map(collectModuleDirectories(module), psiDir -> psiDir.getVirtualFile()));
  }

  FileRecursiveIterator(@NotNull Project project) {
    this(project, ContainerUtil.<PsiDirectory, VirtualFile>map(collectProjectDirectories(project), psiDir -> psiDir.getVirtualFile()));
  }

  FileRecursiveIterator(@NotNull PsiDirectory directory) {
    this(directory.getProject(), Collections.singletonList(directory.getVirtualFile()));
  }

  FileRecursiveIterator(@NotNull Project project, @NotNull Collection<? extends VirtualFile> roots) {
    myProject = project;
    myRoots = roots;
  }

  static @NotNull List<PsiDirectory> collectProjectDirectories(@NotNull Project project) {
    Module[] modules = ModuleManager.getInstance(project).getModules();
    List<PsiDirectory> directories = new ArrayList<>(modules.length*3);
    for (Module module : modules) {
      directories.addAll(collectModuleDirectories(module));
    }

    return directories;
  }

  boolean processAll(@NotNull Processor<? super PsiFile> processor) {
    VirtualFileSet visited = VfsUtilCore.createCompactVirtualFileSet();
    for (VirtualFile root : myRoots) {
      if (!ProjectRootManager.getInstance(myProject).getFileIndex().iterateContentUnderDirectory(root, fileOrDir -> {
        if (fileOrDir.isDirectory() || !visited.add(fileOrDir)) {
          return true;
        }
        PsiFile psiFile = ReadAction.compute(() -> myProject.isDisposed() ? null : PsiManager.getInstance(myProject).findFile(fileOrDir));
        return psiFile == null || processor.process(psiFile);
      })) return false;
    }
    return true;
  }

  static @NotNull List<PsiDirectory> collectModuleDirectories(Module module) {
    VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
    return ReadAction.compute(() -> ContainerUtil.mapNotNull(contentRoots, root -> PsiManager.getInstance(module.getProject()).findDirectory(root)));
  }
}
