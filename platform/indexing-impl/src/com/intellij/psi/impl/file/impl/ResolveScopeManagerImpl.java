/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.psi.impl.file.impl;

import com.intellij.injected.editor.VirtualFileWindow;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.containers.ConcurrentFactoryMap;
import com.intellij.util.indexing.AdditionalIndexableFileSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;

public class ResolveScopeManagerImpl extends ResolveScopeManager {
  private final Project myProject;
  private final ProjectRootManager myProjectRootManager;
  private final PsiManager myManager;

  private final Map<VirtualFile, GlobalSearchScope> myDefaultResolveScopesCache = new ConcurrentFactoryMap<VirtualFile, GlobalSearchScope>() {
    @Override
    protected GlobalSearchScope create(@NotNull VirtualFile key) {
      GlobalSearchScope scope = null;
      for(ResolveScopeProvider resolveScopeProvider: ResolveScopeProvider.EP_NAME.getExtensions()) {
        scope = resolveScopeProvider.getResolveScope(key, myProject);
        if (scope != null) break;
      }
      if (scope == null) scope = getInherentResolveScope(key);
      for (ResolveScopeEnlarger enlarger : ResolveScopeEnlarger.EP_NAME.getExtensions()) {
        final SearchScope extra = enlarger.getAdditionalResolveScope(key, myProject);
        if (extra != null) {
          scope = scope.union(extra);
        }
      }

      return scope;
    }
  };
  private final AdditionalIndexableFileSet myAdditionalIndexableFileSet;

  public ResolveScopeManagerImpl(Project project, ProjectRootManager projectRootManager, PsiManager psiManager) {
    myProject = project;
    myProjectRootManager = projectRootManager;
    myManager = psiManager;
    myAdditionalIndexableFileSet = new AdditionalIndexableFileSet(project);

    ((PsiManagerImpl) psiManager).registerRunnableToRunOnChange(new Runnable() {
      @Override
      public void run() {
        myDefaultResolveScopesCache.clear();
      }
    });

  }

  private GlobalSearchScope getResolveScopeFromProviders(@NotNull final VirtualFile vFile) {
    return myDefaultResolveScopesCache.get(vFile);
  }

  private GlobalSearchScope getInherentResolveScope(VirtualFile vFile) {
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean includeTests = projectFileIndex.isInTestSourceContent(vFile);
      return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
    }

    if (!projectFileIndex.isInLibrarySource(vFile) && !projectFileIndex.isInLibraryClasses(vFile)) {
      return GlobalSearchScope.allScope(myProject);
    }
    
    return LibraryScopeCache.getInstance(myProject).getLibraryScope(projectFileIndex.getOrderEntriesForFile(vFile));
  }

  @Override
  @NotNull
  public GlobalSearchScope getResolveScope(@NotNull PsiElement element) {
    ProgressIndicatorProvider.checkCanceled();

    VirtualFile vFile;
    final PsiFile contextFile;
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
      contextFile = null;
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile instanceof PsiCodeFragment) {
        final GlobalSearchScope forcedScope = ((PsiCodeFragment)containingFile).getForcedResolveScope();
        if (forcedScope != null) {
          return forcedScope;
        }
      }

      if (containingFile != null) {
        PsiElement context = containingFile.getContext();
        if (context != null) {
          return getResolveScope(context);
        }
      }

      contextFile = containingFile;
      if (containingFile == null) {
        return GlobalSearchScope.allScope(myProject);
      }
      else if (contextFile instanceof FileResolveScopeProvider) {
        return ((FileResolveScopeProvider) contextFile).getFileResolveScope();
      }
      vFile = contextFile.getOriginalFile().getVirtualFile();
    }
    if (vFile == null || contextFile == null) {
      return GlobalSearchScope.allScope(myProject);
    }

    return getResolveScopeFromProviders(vFile);
  }


  @Override
  public GlobalSearchScope getDefaultResolveScope(final VirtualFile vFile) {
    final PsiFile psiFile = myManager.findFile(vFile);
    assert psiFile != null;
    return getResolveScopeFromProviders(vFile);
  }


  @Override
  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    VirtualFile vDirectory;
    final VirtualFile virtualFile;
    final PsiFile containingFile;
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(myManager.getProject());
    if (element instanceof PsiDirectory) {
      vDirectory = ((PsiDirectory)element).getVirtualFile();
      virtualFile = null;
      containingFile = null;
    }
    else {
      containingFile = element.getContainingFile();
      if (containingFile == null) return allScope;
      virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return allScope;
      if (virtualFile instanceof VirtualFileWindow) {
        return GlobalSearchScope.fileScope(myProject, ((VirtualFileWindow)virtualFile).getDelegate());
      }
      vDirectory = virtualFile.getParent();
    }

    if (vDirectory == null) return allScope;
    final ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    final Module module = projectFileIndex.getModuleForFile(vDirectory);
    if (module == null) {
      VirtualFile notNullVFile = virtualFile != null ? virtualFile : vDirectory;
      final List<OrderEntry> entries = projectFileIndex.getOrderEntriesForFile(notNullVFile);
      if (entries.isEmpty() && myAdditionalIndexableFileSet.isInSet(notNullVFile)) {
        return allScope;
      }

      GlobalSearchScope result = LibraryScopeCache.getInstance(myProject).getLibraryUseScope(entries);
      return containingFile == null || virtualFile.isDirectory() || result.contains(virtualFile)
             ? result : GlobalSearchScope.fileScope(containingFile).uniteWith(result);
    }
    boolean isTest = projectFileIndex.isInTestSourceContent(vDirectory);
    GlobalSearchScope scope = isTest
                              ? GlobalSearchScope.moduleTestsWithDependentsScope(module)
                              : GlobalSearchScope.moduleWithDependentsScope(module);
    RefResolveService resolveService;
    if (virtualFile instanceof VirtualFileWithId && RefResolveService.ENABLED && (resolveService = RefResolveService.getInstance(myProject)).isUpToDate()) {
      return resolveService.restrictByBackwardIds(virtualFile, scope);
    }
    return scope;
  }
}
