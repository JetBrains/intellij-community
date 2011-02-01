/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.roots.libraries.scripting;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataCache;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Set;

/**
 * @author Rustam Vishnyakov
 */
public class ScriptingLibrariesScope extends GlobalSearchScope {
  private static final UserDataCache<CachedValue<Set<VirtualFile>>, Project, Class<? extends IndexableSetContributor>> ourAllLibFilesCache =
    new UserDataCache<CachedValue<Set<VirtualFile>>, Project, Class<? extends IndexableSetContributor>>() {
    @Override
    protected CachedValue<Set<VirtualFile>> compute(final Project project, final Class<? extends IndexableSetContributor> p) {
      return PsiManager.getInstance(project).getCachedValuesManager().createCachedValue(new CachedValueProvider<Set<VirtualFile>>() {
        @Override
        public Result<Set<VirtualFile>> compute() {
          IndexableSetContributor contributor;
          contributor = IndexableSetContributor.EP_NAME.findExtension(p);
          Set<VirtualFile> result;
          if (contributor instanceof ScriptingIndexableSetContributor) {
            result = ((ScriptingIndexableSetContributor)contributor).getLibraryFiles(project);
          } else {
            result = Collections.emptySet();
          }
          return new Result<Set<VirtualFile>>(result, ProjectRootManager.getInstance(project));
        }
      }, false);
    }
  };

  private GlobalSearchScope myBaseScope;
  protected Set<VirtualFile> myLibraryFiles;
  private Set<VirtualFile> myAllLibraryFiles;

  public ScriptingLibrariesScope(GlobalSearchScope baseScope, Class<? extends IndexableSetContributor> providerClass, Key key) {
    super(baseScope.getProject());
    myBaseScope = baseScope;
    myAllLibraryFiles = ourAllLibFilesCache.get(key, baseScope.getProject(), providerClass).getValue();
    setLibraryFiles();
  }

  protected void setLibraryFiles() {
    myLibraryFiles = myAllLibraryFiles;
  }

  public boolean contains(VirtualFile file) {
    //
    // exclude library files from base scope
    //
    if (myAllLibraryFiles != null && myAllLibraryFiles.contains(file)) {
      return myLibraryFiles == null ? false : myLibraryFiles.contains(file); 
    }
    return myBaseScope.contains(file);
  }

  @Override
  public boolean isSearchOutsideRootModel() {
    return true;
  }

  public int compare(VirtualFile file1, VirtualFile file2) {
    return myBaseScope.compare(file1, file2);
  }

  public boolean isSearchInModuleContent(@NotNull Module aModule) {
    return myBaseScope.isSearchInModuleContent(aModule);
  }

  public boolean isSearchInLibraries() {
    return myBaseScope.isSearchInLibraries();
  }
}
