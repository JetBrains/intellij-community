/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.roots.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.file.exclude.ProjectFileExclusionManager;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileFilter;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class ProjectFileIndexImpl implements ProjectFileIndex {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.roots.impl.ProjectFileIndexImpl");

  private final Project myProject;
  private final FileTypeManager myFileTypeManager;
  private final DirectoryIndex myDirectoryIndex;
  private final ContentFilter myContentFilter;
  private final ProjectFileExclusionManager myFileExclusionManager;

  public ProjectFileIndexImpl(@NotNull Project project, @NotNull DirectoryIndex directoryIndex, @NotNull FileTypeManager fileTypeManager) {
    myProject = project;

    myDirectoryIndex = directoryIndex;
    myFileTypeManager = fileTypeManager;
    myContentFilter = new ContentFilter();
    myFileExclusionManager = ProjectFileExclusionManager.getInstance(project);
  }

  public boolean iterateContent(@NotNull ContentIterator iterator) {
    Module[] modules = ModuleManager.getInstance(myProject).getModules();
    for (Module module : modules) {
      VirtualFile[] contentRoots = ModuleRootManager.getInstance(module).getContentRoots();
      for (VirtualFile contentRoot : contentRoots) {
        DirectoryInfo info = getInfoForDirectory(contentRoot);
        if (info == null) continue; // is excluded or ignored
        if (!module.equals(info.module)) continue; // maybe 2 modules have the same content root?

        VirtualFile parent = contentRoot.getParent();
        if (parent != null) {
          DirectoryInfo parentInfo = getInfoForDirectory(parent);
          if (parentInfo != null && parentInfo.module != null) continue; // inner content - skip it
        }

        boolean finished = FileIndexImplUtil.iterateRecursively(contentRoot, myContentFilter, iterator);
        if (!finished) return false;
      }
    }

    return true;
  }

  private DirectoryInfo getInfoForDirectory(@NotNull VirtualFile file) {
    //if ((! myProject.isOpen()) || myProject.isDisposed()) {
    //  if (StartupManagerEx.getInstanceEx(myProject).startupActivityPassed()) {
    //    throw new ProcessCanceledException();
    //  }
    //}
    return myDirectoryIndex.getInfoForDirectory(file);
  }

  public boolean iterateContentUnderDirectory(@NotNull VirtualFile dir, @NotNull ContentIterator iterator) {
    return FileIndexImplUtil.iterateRecursively(dir, myContentFilter, iterator);
  }

  public boolean isIgnored(@NotNull VirtualFile file) {
    if (myFileTypeManager.isFileIgnored(file)) return true;
    if (myFileExclusionManager != null && myFileExclusionManager.isExcluded(file)) return true;
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return false;

    DirectoryInfo info = getInfoForDirectory(dir);
    if (info != null) return false;
    if (myDirectoryIndex.isProjectExcludeRoot(dir)) return true;

    VirtualFile parent = dir.getParent();
    while (true) {
      if (parent == null) return false;
      DirectoryInfo parentInfo = getInfoForDirectory(parent);
      if (parentInfo != null) return true;
      if (myDirectoryIndex.isProjectExcludeRoot(parent)) return true;
      parent = parent.getParent();
    }
  }

  public Module getModuleForFile(@NotNull VirtualFile file) {
    if (file instanceof VirtualFileWindow) file = ((VirtualFileWindow)file).getDelegate();
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return null;
    return info.module;
  }

  @NotNull
  public List<OrderEntry> getOrderEntriesForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return Collections.emptyList();
    final DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return Collections.emptyList();
    return Collections.unmodifiableList(info.getOrderEntries());
  }

  public VirtualFile getClassRootForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return null;
    return info.libraryClassRoot;
  }

  public VirtualFile getSourceRootForFile(@NotNull VirtualFile file) {
    final VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return null;
    return info.sourceRoot;
  }

  public VirtualFile getContentRootForFile(@NotNull VirtualFile file) {
    VirtualFile dir = file.isDirectory() ? file : file.getParent();
    if (dir == null) return null;
    final DirectoryInfo info = getInfoForDirectory(dir);
    if (info == null) return null;
    return info.contentRoot;
  }

  public String getPackageNameByDirectory(@NotNull VirtualFile dir) {
    LOG.assertTrue(dir.isDirectory());
    return myDirectoryIndex.getPackageName(dir);
  }

  public boolean isContentJavaSourceFile(@NotNull VirtualFile file) {
    return !file.isDirectory() &&
           myFileTypeManager.getFileTypeByFile(file) == StdFileTypes.JAVA &&
           !myFileTypeManager.isFileIgnored(file) &&
           isInSourceContent(file);
  }

  public boolean isLibraryClassFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) return false;
    if (myFileTypeManager.getFileTypeByFile(file) != StdFileTypes.CLASS) return false;
    if (myFileTypeManager.isFileIgnored(file)) return false;
    VirtualFile parent = file.getParent();
    DirectoryInfo parentInfo = getInfoForDirectory(parent);
    return parentInfo != null && parentInfo.libraryClassRoot != null;
  }

  public boolean isInSource(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && (info.isInModuleSource || info.isInLibrarySource);
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSource(parent);
    }
  }

  public boolean isInLibraryClasses(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && info.libraryClassRoot != null;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibraryClasses(parent);
    }
  }

  public boolean isInLibrarySource(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && info.isInLibrarySource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInLibrarySource(parent);
    }
  }

  public boolean isInContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && info.module != null;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInContent(parent);
    }
  }

  public boolean isInSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInSourceContent(parent);
    }
  }

  public boolean isInTestSourceContent(@NotNull VirtualFile fileOrDir) {
    if (fileOrDir.isDirectory()) {
      DirectoryInfo info = getInfoForDirectory(fileOrDir);
      return info != null && info.isInModuleSource && info.isTestSource;
    }
    else {
      VirtualFile parent = fileOrDir.getParent();
      return parent != null && isInTestSourceContent(parent);
    }
  }

  private class ContentFilter implements VirtualFileFilter {
    public boolean accept(@NotNull VirtualFile file) {
      if (file.isDirectory()) {
        DirectoryInfo info = getInfoForDirectory(file);
        return info != null && info.module != null;
      }
      else {
        return (myFileExclusionManager == null || !myFileExclusionManager.isExcluded(file))
               && !myFileTypeManager.isFileIgnored(file);
      }
    }
  }
}
