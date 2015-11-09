/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.core;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.EverythingGlobalScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.ProjectScopeBuilder;
import com.intellij.psi.search.ProjectScopeImpl;
import org.jetbrains.annotations.NotNull;

/**
 * @author yole
 */
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

  private class CoreLibrariesScope extends GlobalSearchScope {
    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndexFacade.isInLibraryClasses(file) || myFileIndexFacade.isInLibrarySource(file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
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
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }
  }
}
