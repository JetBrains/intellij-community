/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.psi.search;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.TestSourcesFilter;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.List;

public class GlobalSearchScopesCore {
  @NotNull
  public static GlobalSearchScope projectProductionScope(@NotNull Project project) {
    return new ProductionScopeFilter(project);
  }

  @NotNull
  public static GlobalSearchScope projectTestScope(@NotNull Project project) {
    return new TestScopeFilter(project);
  }

  @NotNull
  public static GlobalSearchScope directoryScope(@NotNull PsiDirectory directory, final boolean withSubdirectories) {
    return new DirectoryScope(directory, withSubdirectories);
  }

  @NotNull
  public static GlobalSearchScope directoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
    return new DirectoryScope(project, directory, withSubdirectories);
  }

  @NotNull
  public static GlobalSearchScope directoriesScope(@NotNull Project project, boolean withSubdirectories, @NotNull VirtualFile... directories) {
    if (directories.length ==1) {
      return directoryScope(project, directories[0], withSubdirectories);
    }
    BitSet withSubdirectoriesBS = new BitSet(directories.length);
    if (withSubdirectories) {
      withSubdirectoriesBS.set(0, directories.length);
    }
    return new DirectoriesScope(project, directories, withSubdirectoriesBS);
  }

  public static GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return new FilterScopeAdapter(project, set);
  }

  private static class FilterScopeAdapter extends GlobalSearchScope {
    private final NamedScope mySet;
    private final PsiManager myManager;

    private FilterScopeAdapter(@NotNull Project project, @NotNull NamedScope set) {
      super(project);
      mySet = set;
      myManager = PsiManager.getInstance(project);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      Project project = getProject();
      NamedScopesHolder holder = NamedScopeManager.getInstance(project);
      final PackageSet packageSet = mySet.getValue();
      if (packageSet != null) {
        if (packageSet instanceof PackageSetBase) return ((PackageSetBase)packageSet).contains(file, project, holder);
        PsiFile psiFile = myManager.findFile(file);
        return psiFile != null && packageSet.contains(psiFile, holder);
      }
      return false;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return mySet.getName();
    }

    @NotNull
    @Override
    public Project getProject() {
      return super.getProject();
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;

    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; //TODO (optimization?)
    }

    @Override
    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }
  }

  private static class ProductionScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private ProductionScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndex.isInSourceContent(file) && !TestSourcesFilter.isTestSources(file, ObjectUtils.assertNotNull(getProject()));
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
    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return !testSources;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return PsiBundle.message("psi.search.scope.production.files");
    }
  }

  private static class TestScopeFilter extends GlobalSearchScope {
    private TestScopeFilter(@NotNull Project project) {
      super(project);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return TestSourcesFilter.isTestSources(file, ObjectUtils.assertNotNull(getProject()));
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
    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return testSources;
    }

    @Override
    public boolean isSearchInLibraries() {
      return false;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return PsiBundle.message("psi.search.scope.test.files");
    }
  }

  private static class DirectoryScope extends GlobalSearchScope {
    private final VirtualFile myDirectory;
    private final boolean myWithSubdirectories;

    private DirectoryScope(@NotNull PsiDirectory psiDirectory, final boolean withSubdirectories) {
      super(psiDirectory.getProject());
      myWithSubdirectories = withSubdirectories;
      myDirectory = psiDirectory.getVirtualFile();
    }

    private DirectoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
      super(project);
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myWithSubdirectories ? VfsUtilCore.isAncestor(myDirectory, file, false) : myDirectory.equals(file.getParent());
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

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "directory scope: " + myDirectory + "; withSubdirs:"+myWithSubdirectories;
    }

    @Override
    public int hashCode() {
      return myDirectory.hashCode() *31 + (myWithSubdirectories?1:0);
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DirectoryScope &&
             myDirectory.equals(((DirectoryScope)obj).myDirectory) &&
             myWithSubdirectories == ((DirectoryScope)obj).myWithSubdirectories;
    }

    @NotNull
    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (equals(scope)) return this;
      if (scope instanceof DirectoryScope) {
        DirectoryScope other = (DirectoryScope)scope;
        VirtualFile otherDirectory = other.myDirectory;
        if (myWithSubdirectories && VfsUtilCore.isAncestor(myDirectory, otherDirectory, false)) return this;
        if (other.myWithSubdirectories && VfsUtilCore.isAncestor(otherDirectory, myDirectory, false)) return other;
        BitSet newWithSubdirectories = new BitSet();
        newWithSubdirectories.set(0, myWithSubdirectories);
        newWithSubdirectories.set(1, other.myWithSubdirectories);
        return new DirectoriesScope(getProject(), new VirtualFile[]{myDirectory,otherDirectory}, newWithSubdirectories);
      }
      return super.uniteWith(scope);
    }

    @NotNull
    @Override
    public Project getProject() {
      return super.getProject();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "Directory '" + myDirectory.getName() + "'";
    }
  }

  static class DirectoriesScope extends GlobalSearchScope {
    private final VirtualFile[] myDirectories;
    private final BitSet myWithSubdirectories;

    private DirectoriesScope(@NotNull Project project, @NotNull VirtualFile[] directories, @NotNull BitSet withSubdirectories) {
      super(project);
      myWithSubdirectories = withSubdirectories;
      myDirectories = directories;
      if (directories.length < 2) {
        throw new IllegalArgumentException("Expected >1 directories, but got: " + Arrays.asList(directories));
      }
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      VirtualFile parent = file.getParent();
      return parent != null && in(parent);
    }

    private boolean in(@NotNull VirtualFile parent) {
      for (int i = 0; i < myDirectories.length; i++) {
        VirtualFile directory = myDirectories[i];
        boolean withSubdirectories = myWithSubdirectories.get(i);
        if (withSubdirectories ? VfsUtilCore.isAncestor(directory, parent, false) : directory.equals(parent)) return true;
      }
      return false;
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

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "Directories scope: " + Arrays.asList(myDirectories);
    }

    @Override
    public int hashCode() {
      int result = 1;
      for (int i = 0; i < myDirectories.length; i++) {
        VirtualFile directory = myDirectories[i];
        boolean withSubdirectories = myWithSubdirectories.get(i);
        result = result*31 + directory.hashCode() *31 + (withSubdirectories?1:0);
      }
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DirectoriesScope &&
             Arrays.equals(myDirectories, ((DirectoriesScope)obj).myDirectories) &&
             myWithSubdirectories.equals(((DirectoriesScope)obj).myWithSubdirectories);
    }

    @NotNull
    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (equals(scope)) {
        return this;
      }
      if (scope instanceof DirectoryScope) {
        if (in(((DirectoryScope)scope).myDirectory)) {
          return this;
        }
        VirtualFile[] newDirectories = ArrayUtil.append(myDirectories, ((DirectoryScope)scope).myDirectory, VirtualFile.class);
        BitSet newWithSubdirectories = (BitSet)myWithSubdirectories.clone();
        newWithSubdirectories.set(myDirectories.length, ((DirectoryScope)scope).myWithSubdirectories);
        return new DirectoriesScope(getProject(), newDirectories, newWithSubdirectories);
      }
      if (scope instanceof DirectoriesScope) {
        DirectoriesScope other = (DirectoriesScope)scope;
        List<VirtualFile> newDirectories = new ArrayList<>(myDirectories.length + other.myDirectories.length);
        newDirectories.addAll(Arrays.asList(other.myDirectories));
        BitSet newWithSubdirectories = (BitSet)myWithSubdirectories.clone();
        VirtualFile[] directories = other.myDirectories;
        for (int i = 0; i < directories.length; i++) {
          VirtualFile otherDirectory = directories[i];
          if (!in(otherDirectory)) {
            newWithSubdirectories.set(newDirectories.size(), other.myWithSubdirectories.get(i));
            newDirectories.add(otherDirectory);
          }
        }
        return new DirectoriesScope(getProject(), newDirectories.toArray(new VirtualFile[newDirectories.size()]), newWithSubdirectories);
      }
      return super.uniteWith(scope);
    }

    @NotNull
    @Override
    public Project getProject() {
      return super.getProject();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      if (myDirectories.length == 1) {
        VirtualFile root = myDirectories[0];
        return "Directory '" + root.getName() + "'";
      }
      return "Directories " + StringUtil.join(myDirectories, file -> "'" + file.getName() + "'", ", ");
    }

  }
}
