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
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.UnloadedModuleDescription;
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
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

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
    if (directories.length == 1) {
      return directoryScope(project, directories[0], withSubdirectories);
    }
    return new DirectoriesScope(project,
                                withSubdirectories ? Collections.emptySet() : ContainerUtil.newHashSet(directories),
                                withSubdirectories ? ContainerUtil.newHashSet(directories) : Collections.emptySet());
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
      //noinspection ConstantConditions
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

    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return ModuleManager.getInstance(ObjectUtils.assertNotNull(getProject())).getUnloadedModuleDescriptions();
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
      VirtualFile parent = file.getParent();
      return parent != null && in(parent);
    }

    private boolean in(@NotNull VirtualFile parent) {
      return myWithSubdirectories ? VfsUtilCore.isAncestor(myDirectory, parent, false) : myDirectory.equals(parent);
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
      return true;
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
        if (in(other.myDirectory)) return this;
        if (other.in(myDirectory)) return other;
        return new DirectoriesScope(getProject(),
                                    union(!myWithSubdirectories, myDirectory, !other.myWithSubdirectories, other.myDirectory),
                                    union(myWithSubdirectories, myDirectory, other.myWithSubdirectories, other.myDirectory));
      }
      return super.uniteWith(scope);
    }

    private static Set<VirtualFile> union(boolean addDir1, @NotNull VirtualFile dir1, boolean addDir2, @NotNull VirtualFile dir2) {
      if (addDir1 && addDir2) return ContainerUtil.newHashSet(dir1, dir2);
      if (addDir1) return Collections.singleton(dir1);
      if (addDir2) return Collections.singleton(dir2);
      return Collections.emptySet();
    }

    @NotNull
    @Override
    public Project getProject() {
      //noinspection ConstantConditions
      return super.getProject();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return "Directory '" + myDirectory.getName() + "'";
    }
  }

  static class DirectoriesScope extends GlobalSearchScope {
    private final Set<VirtualFile> myDirectories;
    private final Set<VirtualFile> myDirectoriesWithSubdirectories;

    private DirectoriesScope(@NotNull Project project,
                             @NotNull Set<VirtualFile> directories,
                             @NotNull Set<VirtualFile> directoriesWithSubdirectories) {
      super(project);
      myDirectories = directories;
      myDirectoriesWithSubdirectories = directoriesWithSubdirectories;
      if (directories.size() + directoriesWithSubdirectories.size() < 2) {
        throw new IllegalArgumentException("Expected >1 directories, but got: directories " + directories
                                           + ", directories with subdirectories " + directoriesWithSubdirectories);
      }
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      VirtualFile parent = file.getParent();
      return parent != null && in(parent);
    }

    private boolean in(@NotNull VirtualFile parent) {
      if (myDirectories.contains(parent)) {
        return true;
      }
      return VfsUtilCore.isUnder(parent, myDirectoriesWithSubdirectories);
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
      return true;
    }

    @Override
    public String toString() {
      //noinspection HardCodedStringLiteral
      return "Directories scope: directories " + myDirectories + ", directories with subdirectories " + myDirectoriesWithSubdirectories;
    }

    @Override
    public int hashCode() {
      int result = myDirectories.hashCode();
      result = result * 31 + myDirectoriesWithSubdirectories.hashCode();
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      return obj instanceof DirectoriesScope &&
             myDirectories.equals(((DirectoriesScope)obj).myDirectories) &&
             myDirectoriesWithSubdirectories.equals(((DirectoriesScope)obj).myDirectoriesWithSubdirectories);
    }

    @NotNull
    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (equals(scope)) {
        return this;
      }
      if (scope instanceof DirectoryScope) {
        DirectoryScope other = (DirectoryScope)scope;
        if (in(other.myDirectory)) {
          return this;
        }
        Set<VirtualFile> directories = myDirectories;
        Set<VirtualFile> directoriesWithSubdirectories = myDirectoriesWithSubdirectories;
        if (other.myWithSubdirectories) {
          directoriesWithSubdirectories = new HashSet<>(directoriesWithSubdirectories);
          directoriesWithSubdirectories.add(other.myDirectory);
        }
        else {
          directories = new HashSet<>(directories);
          directories.add(other.myDirectory);
        }
        return new DirectoriesScope(getProject(), directories, directoriesWithSubdirectories);
      }
      if (scope instanceof DirectoriesScope) {
        DirectoriesScope other = (DirectoriesScope)scope;
        Set<VirtualFile> directories = myDirectories;
        Set<VirtualFile> directoriesWithSubdirectories = myDirectoriesWithSubdirectories;
        if (!other.myDirectories.isEmpty()) {
          directories = new HashSet<>(directories);
          directories.addAll(other.myDirectories);
        }
        if (!other.myDirectoriesWithSubdirectories.isEmpty()) {
          directoriesWithSubdirectories = new HashSet<>(directoriesWithSubdirectories);
          directoriesWithSubdirectories.addAll(other.myDirectoriesWithSubdirectories);
        }
        return new DirectoriesScope(getProject(), directories, directoriesWithSubdirectories);
      }
      return super.uniteWith(scope);
    }

    @NotNull
    @Override
    public Project getProject() {
      //noinspection ConstantConditions
      return super.getProject();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      if (myDirectories.size() + myDirectoriesWithSubdirectories.size() == 1) {
        Set<VirtualFile> dirs = myDirectories.size() == 1 ? myDirectories : myDirectoriesWithSubdirectories;
        VirtualFile root = Objects.requireNonNull(ContainerUtil.getFirstItem(dirs));
        return "Directory '" + root.getName() + "'";
      }
      Iterable<VirtualFile> allDirs = ContainerUtil.concat(myDirectories, myDirectoriesWithSubdirectories);
      return "Directories " + StringUtil.join(allDirs, file -> "'" + file.getName() + "'", ", ");
    }

  }
}
