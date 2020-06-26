// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.analysis.AnalysisBundle;
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
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.scope.packageSet.*;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.*;

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
  public static GlobalSearchScope directoriesScope(@NotNull Project project, boolean withSubdirectories, VirtualFile @NotNull ... directories) {
    Set<VirtualFile> dirSet = ContainerUtil.newHashSet(directories);
    if (dirSet.isEmpty()) {
      return GlobalSearchScope.EMPTY_SCOPE;
    }
    if (dirSet.size() == 1) {
      return directoryScope(project, dirSet.iterator().next(), withSubdirectories);
    }
    return new DirectoriesScope(project,
                                withSubdirectories ? Collections.emptySet() : dirSet,
                                withSubdirectories ? dirSet : Collections.emptySet());
  }

  public static @NotNull GlobalSearchScope filterScope(@NotNull Project project, @NotNull NamedScope set) {
    return new FilterScopeAdapter(project, set);
  }

  private static final class FilterScopeAdapter extends GlobalSearchScope {
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
    public Icon getIcon() {
      return mySet.getIcon();
    }

    @NotNull
    @Override
    public Project getProject() {
      //noinspection ConstantConditions
      return super.getProject();
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; //TODO (optimization?)
    }

    @Override
    public boolean isSearchInLibraries() {
      return true; //TODO (optimization?)
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      FilterScopeAdapter adapter = (FilterScopeAdapter)o;

      if (!mySet.equals(adapter.mySet)) return false;
      return myManager.equals(adapter.myManager);
    }

    @Override
    public int calcHashCode() {
      int result = super.calcHashCode();
      result = 31 * result + mySet.hashCode();
      result = 31 * result + myManager.hashCode();
      return result;
    }

    @Override
    public String toString() {
      return "FilterScope adapted from "+mySet;
    }
  }

  private static final class ProductionScopeFilter extends GlobalSearchScope {
    private final ProjectFileIndex myFileIndex;

    private ProductionScopeFilter(@NotNull Project project) {
      super(project);
      myFileIndex = ProjectRootManager.getInstance(project).getFileIndex();
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myFileIndex.isInSourceContent(file) && !TestSourcesFilter.isTestSources(file, Objects.requireNonNull(getProject()));
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
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return ModuleManager.getInstance(Objects.requireNonNull(getProject())).getUnloadedModuleDescriptions();
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return AnalysisBundle.message("psi.search.scope.production.files");
    }
  }

  private static final class TestScopeFilter extends GlobalSearchScope {
    private TestScopeFilter(@NotNull Project project) {
      super(project);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return TestSourcesFilter.isTestSources(file, Objects.requireNonNull(getProject()));
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
      return AnalysisBundle.message("psi.search.scope.test.files");
    }
  }

  public static class DirectoryScope extends GlobalSearchScope {
    private final VirtualFile myDirectory;
    private final boolean myWithSubdirectories;

    private DirectoryScope(@NotNull PsiDirectory psiDirectory, final boolean withSubdirectories) {
      super(psiDirectory.getProject());
      myWithSubdirectories = withSubdirectories;
      myDirectory = psiDirectory.getVirtualFile();
    }

    public DirectoryScope(@NotNull Project project, @NotNull VirtualFile directory, final boolean withSubdirectories) {
      super(project);
      myWithSubdirectories = withSubdirectories;
      myDirectory = directory;
    }

    @NotNull
    public VirtualFile getDirectory() {
      return myDirectory;
    }

    public boolean isWithSubdirectories() {
      return myWithSubdirectories;
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
    public int calcHashCode() {
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

    @NotNull
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
      return AnalysisBundle.message("display.name.directory.0", myDirectory.getName());
    }
  }

  static final class DirectoriesScope extends GlobalSearchScope {
    private final Set<? extends VirtualFile> myDirectories;
    private final Set<? extends VirtualFile> myDirectoriesWithSubdirectories;

    private DirectoriesScope(@NotNull Project project,
                             @NotNull Set<? extends VirtualFile> directories,
                             @NotNull Set<? extends VirtualFile> directoriesWithSubdirectories) {
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
    public int calcHashCode() {
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
        Set<? extends VirtualFile> directories = myDirectories;
        Set<? extends VirtualFile> directoriesWithSubdirectories = myDirectoriesWithSubdirectories;
        if (other.myWithSubdirectories) {
          Set<VirtualFile> copy = new HashSet<>(directoriesWithSubdirectories);
          copy.add(other.myDirectory);
          directoriesWithSubdirectories = copy;
        }
        else {
          Set<VirtualFile> copy = new HashSet<>(directories);
          copy.add(other.myDirectory);
          directories = copy;
        }
        return new DirectoriesScope(getProject(), directories, directoriesWithSubdirectories);
      }
      if (scope instanceof DirectoriesScope) {
        DirectoriesScope other = (DirectoriesScope)scope;
        Set<? extends VirtualFile> directories = myDirectories;
        Set<? extends VirtualFile> directoriesWithSubdirectories = myDirectoriesWithSubdirectories;
        if (!other.myDirectories.isEmpty()) {
          Set<VirtualFile> copy = new HashSet<>(directories);
          copy.addAll(other.myDirectories);
          directories = copy;
        }
        if (!other.myDirectoriesWithSubdirectories.isEmpty()) {
          Set<VirtualFile> copy = new HashSet<>(directoriesWithSubdirectories);
          copy.addAll(other.myDirectoriesWithSubdirectories);
          directoriesWithSubdirectories = copy;
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
        Set<? extends VirtualFile> dirs = myDirectories.size() == 1 ? myDirectories : myDirectoriesWithSubdirectories;
        VirtualFile root = Objects.requireNonNull(ContainerUtil.getFirstItem(dirs));
        return AnalysisBundle.message("display.name.directory.0", root.getName());
      }
      Iterable<VirtualFile> allDirs = ContainerUtil.concat(myDirectories, myDirectoriesWithSubdirectories);
      return AnalysisBundle.message("display.name.directories.0", StringUtil.join(allDirs, file -> "'" + file.getName() + "'", ", "));
    }

  }
}
