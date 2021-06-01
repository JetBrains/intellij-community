// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.search.ProjectScopeImpl;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;


public class CoreProjectScopeBuilder extends ProjectScopeBuilder {
  private final Project myProject;
  private final FileIndexFacade myFileIndexFacade;
  private final CoreProjectScopeBuilder.CoreLibrariesScope myLibrariesScope;

  public CoreProjectScopeBuilder(Project project, FileIndexFacade fileIndexFacade) {
    myFileIndexFacade = fileIndexFacade;
    myProject = project;
    myLibrariesScope = new CoreLibrariesScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope buildLibrariesScope() {
    return myLibrariesScope;
  }

  @NotNull
  @Override
  public GlobalSearchScope buildAllScope() {
    return new EverythingGlobalScope();
  }

  @NotNull
  @Override
  public GlobalSearchScope buildProjectScope() {
    return new ProjectScopeImpl(myProject, myFileIndexFacade);
  }

  @NotNull
  @Override
  public GlobalSearchScope buildContentScope() {
    return new ContentSearchScope(myProject, myFileIndexFacade);
  }

  @NotNull
  @Override
  public GlobalSearchScope buildEverythingScope() {
    return new EverythingGlobalScope(myProject);
  }

  private class CoreLibrariesScope extends GlobalSearchScope {
    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndexFacade.isInLibraryClasses(file) || myFileIndexFacade.isInLibrarySource(file);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return false;
    }

    @Override
    public boolean isSearchInLibraries() {
      return true;
    }
  }

  public static class ContentSearchScope extends GlobalSearchScope {

    private final FileIndexFacade myFileIndexFacade;

    public ContentSearchScope(Project project, FileIndexFacade fileIndexFacade) {
      super(project);
      myFileIndexFacade = fileIndexFacade;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndexFacade.isInContent(file);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @NotNull
    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return myFileIndexFacade.getUnloadedModuleDescriptions();
    }
  }
}
