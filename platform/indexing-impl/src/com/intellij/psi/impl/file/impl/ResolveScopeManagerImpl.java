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
package com.intellij.psi.impl.file.impl;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.impl.scopes.LibraryRuntimeClasspathScope;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.LibraryScopeCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.ResolveScopeManager;
import com.intellij.psi.impl.source.resolve.FileContextUtil;
import com.intellij.psi.search.DelegatingGlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.containers.ConcurrentFactoryMap;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ResolveScopeManagerImpl extends ResolveScopeManager {

  private final Project myProject;
  private final ProjectRootManager myProjectRootManager;
  private final PsiManager myManager;

  private final Map<VirtualFile, GlobalSearchScope> myDefaultResolveScopesCache = new ConcurrentFactoryMap<VirtualFile, GlobalSearchScope>() {
    @Override
    protected GlobalSearchScope create(VirtualFile key) {
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

  public ResolveScopeManagerImpl(Project project, ProjectRootManager projectRootManager, PsiManager psiManager) {
    myProject = project;
    myProjectRootManager = projectRootManager;
    myManager = psiManager;

    ((PsiManagerImpl) psiManager).registerRunnableToRunOnChange(new Runnable() {
      @Override
      public void run() {
        myDefaultResolveScopesCache.clear();
      }
    });

  }

  private GlobalSearchScope getDefaultResolveScope(@NotNull PsiFile psiFile, @NotNull final VirtualFile vFile) {
    return myDefaultResolveScopesCache.get(vFile);
  }

  private GlobalSearchScope getInherentResolveScope(VirtualFile vFile) {
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean includeTests = projectFileIndex.isInTestSourceContent(vFile);
                             // TODO: dmitrylomov: removed this line to see what fails.
                             //!(vFile.getFileType() == StdFileTypes.JAVA && projectFileIndex.isContentSourceFile(vFile));
      return GlobalSearchScope.moduleWithDependenciesAndLibrariesScope(module, includeTests);
    }
    else {
      // resolve references in libraries in context of all modules which contain it
      List<Module> modulesLibraryUsedIn = new ArrayList<Module>();
      List<OrderEntry> orderEntries = projectFileIndex.getOrderEntriesForFile(vFile);

      LibraryOrderEntry lib = null;
      for (OrderEntry entry : orderEntries) {
        if (entry instanceof JdkOrderEntry) {
          return LibraryScopeCache.getInstance(myProject).getScopeForSdk((JdkOrderEntry)entry);
        }

        if (entry instanceof LibraryOrderEntry) {
          lib = (LibraryOrderEntry)entry;
          modulesLibraryUsedIn.add(entry.getOwnerModule());
        }
        else if (entry instanceof ModuleOrderEntry) {
          modulesLibraryUsedIn.add(entry.getOwnerModule());
        }
      }

      GlobalSearchScope allCandidates = LibraryScopeCache.getInstance(myProject).getScopeForLibraryUsedIn(modulesLibraryUsedIn);
      if (lib != null) {
        final LibraryRuntimeClasspathScope preferred = new LibraryRuntimeClasspathScope(myProject, lib);
        // prefer current library
        return new DelegatingGlobalSearchScope(allCandidates, preferred) {
          @Override
          public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
            boolean c1 = preferred.contains(file1);
            boolean c2 = preferred.contains(file2);
            if (c1 && !c2) return 1;
            if (c2 && !c1) return -1;

            return super.compare(file1, file2);
          }
        };
      }
      return allCandidates;
    }
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
        final PsiElement context = containingFile.getContext();
        if (context == null) {
          return GlobalSearchScope.allScope(myProject);
        }
        return getResolveScope(context);
      }

      contextFile = containingFile != null ? FileContextUtil.getContextFile(containingFile) : null;
      if (contextFile == null) {
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

    return getDefaultResolveScope(contextFile, vFile);
  }


  @Override
  public GlobalSearchScope getDefaultResolveScope(final VirtualFile vFile) {
    final PsiFile psiFile = myManager.findFile(vFile);
    assert psiFile != null;
    return getDefaultResolveScope(psiFile, vFile);
  }


  @Override
  @NotNull
  public GlobalSearchScope getUseScope(@NotNull PsiElement element) {
    VirtualFile vFile;
    final GlobalSearchScope allScope = GlobalSearchScope.allScope(myManager.getProject());
    if (element instanceof PsiDirectory) {
      vFile = ((PsiDirectory)element).getVirtualFile();
    }
    else {
      final PsiFile containingFile = element.getContainingFile();
      if (containingFile == null) return allScope;
      final VirtualFile virtualFile = containingFile.getVirtualFile();
      if (virtualFile == null) return allScope;
      vFile = virtualFile.getParent();
    }

    if (vFile == null) return allScope;
    ProjectFileIndex projectFileIndex = myProjectRootManager.getFileIndex();
    Module module = projectFileIndex.getModuleForFile(vFile);
    if (module != null) {
      boolean isTest = projectFileIndex.isInTestSourceContent(vFile);
      return isTest
             ? GlobalSearchScope.moduleTestsWithDependentsScope(module)
             : GlobalSearchScope.moduleWithDependentsScope(module);
    }
    else {
      final PsiFile f = element.getContainingFile();
      final VirtualFile vf = f == null ? null : f.getVirtualFile();

      return f == null || vf == null || vf.isDirectory() || allScope.contains(vf)
             ? allScope : GlobalSearchScope.fileScope(f).uniteWith(allScope);
    }
  }
}
