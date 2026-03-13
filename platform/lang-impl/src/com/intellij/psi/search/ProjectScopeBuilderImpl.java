// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.core.CoreProjectScopeBuilder;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.scratch.RootType;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.CacheAvoidingVirtualFile;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;


public class ProjectScopeBuilderImpl extends ProjectScopeBuilder {
  protected final @NotNull Project myProject;

  public ProjectScopeBuilderImpl(@NotNull Project project) {
    myProject = project;
  }

  @Override
  public @NotNull GlobalSearchScope buildEverythingScope() {
    return new EverythingGlobalScope(myProject) {
      final FileBasedIndexImpl myFileBasedIndex;
      final WorkspaceFileIndex myWorkspaceFileIndex = WorkspaceFileIndex.getInstance(myProject);

      {
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        // handle case of EmptyFileBasedIndex
        myFileBasedIndex = !(fileBasedIndex instanceof FileBasedIndexImpl)
                           ? null
                           : (FileBasedIndexImpl)fileBasedIndex;
      }

      /// This method checks if a file belongs to the workspace.
      ///
      /// It accepts 4 types of files:
      /// 1. Scratch files that are not hidden
      /// 2. Indexable files
      /// 3. Files in the workspace
      /// 4. Every file without id, unless it is (1)
      ///
      /// To achieve this, the method performs 3 checks:
      /// 1. If a file is a hidden scratch file. If it is, it's not part of the workspace.
      /// 2. If a file is indexable, it is part of the workspace. This check prevents filtering out files registered by [com.intellij.util.indexing.IndexableSetContributor]
      /// 3. If a file has id or is [CacheAvoidingVirtualFile], it is checked to be part of the workspace. Cache-avoiding files can be without id, so they are explicitly checked.
      ///
      /// If none of the above is applied to the file, it is considered to be part of the workspace. This is for historical reasons, to allow [com.intellij.testFramework.LightVirtualFile] instances, i.e., virtual files without an id
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        RootType rootType = RootType.forFile(file);
        if (rootType != null && (rootType.isHidden() || rootType.isIgnored(myProject, file))) return false;

        if (file instanceof VirtualFileWithId && myFileBasedIndex != null) {
          if (myFileBasedIndex.belongsToProjectIndexableFiles(file, myProject)) return true;
        }

        if (file instanceof VirtualFileWithId || file instanceof CacheAvoidingVirtualFile) {
          return myWorkspaceFileIndex.isInWorkspace(file);
        }
        return true;
      }
    };
  }

  @Override
  public @NotNull GlobalSearchScope buildLibrariesScope() {
    ProjectAndLibrariesScope result = new ProjectAndLibrariesScope(myProject) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return myProjectFileIndex.isInLibrary(file);
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      @Override
      public @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
        return Collections.emptySet();
      }
    };
    result.setDisplayName(LangBundle.message("psi.search.scope.libraries"));
    return result;
  }

  @Override
  public @NotNull GlobalSearchScope buildAllScope() {
    if (myProject.isDefault() || LightEdit.owns(myProject)) {
      return new EverythingGlobalScope(myProject);
    }

    return new ProjectAndLibrariesScope(myProject) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof ProjectAwareVirtualFile) {
          return ((ProjectAwareVirtualFile)file).isInProject(Objects.requireNonNull(getProject()));
        }
        return super.contains(file);
      }
    };
  }

  @Override
  public @NotNull GlobalSearchScope buildProjectScope() {
    return new ProjectScopeImpl(myProject, FileIndexFacade.getInstance(myProject));
  }

  @Override
  public @NotNull GlobalSearchScope buildContentScope() {
    return new CoreProjectScopeBuilder.ContentSearchScope(myProject, FileIndexFacade.getInstance(myProject));
  }
}
