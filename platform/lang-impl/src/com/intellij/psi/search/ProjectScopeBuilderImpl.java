// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.core.CoreProjectScopeBuilder;
import com.intellij.ide.lightEdit.LightEdit;
import com.intellij.ide.scratch.RootType;
import com.intellij.lang.LangBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.FileBasedIndexImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;


public class ProjectScopeBuilderImpl extends ProjectScopeBuilder {
  @NotNull
  protected final Project myProject;

  public ProjectScopeBuilderImpl(@NotNull Project project) {
    myProject = project;
  }

  @NotNull
  @Override
  public GlobalSearchScope buildEverythingScope() {
    return new EverythingGlobalScope(myProject) {
      final FileBasedIndexImpl myFileBasedIndex;

      {
        boolean unitTestMode = ApplicationManager.getApplication().isUnitTestMode();
        FileBasedIndex fileBasedIndex = FileBasedIndex.getInstance();
        // handle case of EmptyFileBasedIndex
        myFileBasedIndex = unitTestMode && !(fileBasedIndex instanceof FileBasedIndexImpl)
                           ? null
                           : (FileBasedIndexImpl)fileBasedIndex;
      }

      @Override
      public boolean contains(@NotNull VirtualFile file) {
        if (file instanceof VirtualFileWithId && myFileBasedIndex != null) {
          return myFileBasedIndex.belongsToProjectIndexableFiles(file, myProject);
        }

        RootType rootType = RootType.forFile(file);
        if (rootType != null && (rootType.isHidden() || rootType.isIgnored(myProject, file))) return false;
        return true;
      }
    };
  }

  @NotNull
  @Override
  public GlobalSearchScope buildLibrariesScope() {
    ProjectAndLibrariesScope result = new ProjectAndLibrariesScope(myProject) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return myProjectFileIndex.isInLibrary(file);
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return false;
      }

      @NotNull
      @Override
      public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
        return Collections.emptySet();
      }
    };
    result.setDisplayName(LangBundle.message("psi.search.scope.libraries"));
    return result;
  }

  @NotNull
  @Override
  public GlobalSearchScope buildAllScope() {
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

  @NotNull
  @Override
  public GlobalSearchScope buildProjectScope() {
    return new ProjectScopeImpl(myProject, FileIndexFacade.getInstance(myProject));
  }

  @NotNull
  @Override
  public GlobalSearchScope buildContentScope() {
    return new CoreProjectScopeBuilder.ContentSearchScope(myProject, FileIndexFacade.getInstance(myProject));
  }
}
