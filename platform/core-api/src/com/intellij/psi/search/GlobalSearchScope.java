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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiBundle;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public abstract class GlobalSearchScope extends SearchScope implements ProjectAwareFileFilter {
  @Nullable private final Project myProject;

  protected GlobalSearchScope(@Nullable Project project) {
    myProject = project;
  }

  protected GlobalSearchScope() {
    this(null);
  }

  @Nullable
  @Override
  public Project getProject() {
    return myProject;
  }

  /**
   * @return a positive integer (+1), if file1 is located in the classpath before file2,
   *         a negative integer (-1), if file1 is located in the classpath after file2
   *         zero - otherwise or when the files are not comparable.
   */
  public abstract int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2);

  // optimization methods:

  public abstract boolean isSearchInModuleContent(@NotNull Module aModule);

  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule);
  }

  @Override
  public final boolean accept(VirtualFile file) {
    return contains(file);
  }

  public abstract boolean isSearchInLibraries();

  public boolean isForceSearchingInLibrarySources() {
    return false;
  }

  public boolean isSearchOutsideRootModel() {
    return false;
  }

  /**
   * Returns descriptions of unloaded modules content of whose might be included into this scope if they had been loaded. Actually search in
   * unloaded modules isn't performed, so this method is used to determine whether a warning about possible missing results should be shown.
   */
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return Collections.emptySet();
  }

  @NotNull
  public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return this;
    if (scope instanceof IntersectionScope && ((IntersectionScope)scope).containsScope(this)) {
      return scope;
    }
    return new IntersectionScope(this, scope, null);
  }

  @NotNull
  @Override
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    if (scope2 instanceof LocalSearchScope) {
      LocalSearchScope localScope2 = (LocalSearchScope)scope2;
      return intersectWith(localScope2);
    }
    return intersectWith((GlobalSearchScope)scope2);
  }

  @NotNull
  public SearchScope intersectWith(@NotNull LocalSearchScope localScope2) {
    PsiElement[] elements2 = localScope2.getScope();
    List<PsiElement> result = new ArrayList<>(elements2.length);
    for (final PsiElement element2 : elements2) {
      if (PsiSearchScopeUtil.isInScope(this, element2)) {
        result.add(element2);
      }
    }
    return result.isEmpty() ? EMPTY_SCOPE : new LocalSearchScope(result.toArray(new PsiElement[result.size()]), null, localScope2.isIgnoreInjectedPsi());
  }

  @Override
  @NotNull
  public GlobalSearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof GlobalSearchScope) return uniteWith((GlobalSearchScope)scope);
    return union((LocalSearchScope)scope);
  }

  @NotNull
  public GlobalSearchScope union(@NotNull final LocalSearchScope scope) {
    PsiElement[] localScopeElements = scope.getScope();
    if (localScopeElements.length == 0) {
      return this;
    }
    return new GlobalSearchScope(localScopeElements[0].getProject()) {
      @Override
      public boolean contains(@NotNull VirtualFile file) {
        return GlobalSearchScope.this.contains(file) || scope.isInScope(file);
      }

      @Override
      public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
        return GlobalSearchScope.this.contains(file1) && GlobalSearchScope.this.contains(file2) ? GlobalSearchScope.this.compare(file1, file2) : 0;
      }

      @Override
      public boolean isSearchInModuleContent(@NotNull Module aModule) {
        return GlobalSearchScope.this.isSearchInModuleContent(aModule);
      }

      @Override
      public boolean isSearchOutsideRootModel() {
        return GlobalSearchScope.this.isSearchOutsideRootModel();
      }

      @Override
      public boolean isSearchInLibraries() {
        return GlobalSearchScope.this.isSearchInLibraries();
      }

      @Override
      public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
        return GlobalSearchScope.this.getUnloadedModulesBelongingToScope();
      }

      @NonNls
      @Override
      public String toString() {
        return "UnionToLocal: (" + GlobalSearchScope.this + ", " + scope + ")";
      }
    };
  }

  @NotNull
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return scope;

    return new UnionScope(this, scope);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope union(@NotNull GlobalSearchScope[] scopes) {
    if (scopes.length == 0) {
      throw new IllegalArgumentException("Empty scope array");
    }
    if (scopes.length == 1) {
      return scopes[0];
    }
    return new UnionScope(scopes);
  }

  @NotNull
  public static GlobalSearchScope allScope(@NotNull Project project) {
    return ProjectScope.getAllScope(project);
  }

  @NotNull
  public static GlobalSearchScope projectScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  @NotNull
  public static GlobalSearchScope everythingScope(@NotNull Project project) {
    return ProjectScope.getEverythingScope(project);
  }

  @NotNull
  public static GlobalSearchScope notScope(@NotNull final GlobalSearchScope scope) {
    return new NotScope(scope);
  }

  private static class NotScope extends DelegatingGlobalSearchScope {
    private NotScope(@NotNull GlobalSearchScope scope) {
      super(scope);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return !myBaseScope.contains(file);
    }

    @Override
    public boolean isSearchInLibraries() {
      return true; // not (in library A) is perfectly fine to find classes in another library B.
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
      return true; // not (some files in module A) is perfectly fine to find classes in another part of module A.
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true; // not (some files in module A) is perfectly fine to find classes in another part of module A.
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return true;
    }

    @Override
    public String toString() {
      return "NOT: "+myBaseScope;
    }
  }

  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources and tests, excluding libraries and dependencies.
   */
  @NotNull
  public static GlobalSearchScope moduleScope(@NotNull Module module) {
    return module.getModuleScope();
  }

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and libraries, excluding dependencies.
   */
  @NotNull
  public static GlobalSearchScope moduleWithLibrariesScope(@NotNull Module module) {
    return module.getModuleWithLibrariesScope();
  }

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @param module the module to get the scope.
   * @return scope including sources, tests, and dependencies, excluding libraries.
   */
  @NotNull
  public static GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  @NotNull
  public static GlobalSearchScope moduleRuntimeScope(@NotNull Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  @NotNull
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  @NotNull
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @NotNull
  public static GlobalSearchScope moduleWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  @NotNull
  public static GlobalSearchScope moduleTestsWithDependentsScope(@NotNull Module module) {
    return module.getModuleTestsWithDependentsScope();
  }

  @NotNull
  public static GlobalSearchScope fileScope(@NotNull PsiFile psiFile) {
    return new FileScope(psiFile.getProject(), psiFile.getVirtualFile());
  }

  @NotNull
  public static GlobalSearchScope fileScope(@NotNull Project project, final VirtualFile virtualFile) {
    return fileScope(project, virtualFile, null);
  }

  @NotNull
  public static GlobalSearchScope fileScope(@NotNull Project project, @Nullable VirtualFile virtualFile, @Nullable final String displayName) {
    return new FileScope(project, virtualFile) {
      @NotNull
      @Override
      public String getDisplayName() {
        return displayName == null ? super.getDisplayName() : displayName;
      }
    };
  }

  /**
   * Please consider using {@link this#filesWithLibrariesScope} or {@link this#filesWithoutLibrariesScope} for optimization
   */
  @NotNull
  public static GlobalSearchScope filesScope(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return filesScope(project, files, null);
  }

  /**
   * Optimization. By default FilesScope makes a decision about searching in libraries by checking that
   * at least one file is placed out of module roots. So if you're sure about files placement you can explicitly say FilesScope whether
   * it should include libraries or not in order to avoid checking each file.
   * Also, if you have a lot of files it might be faster to always search in libraries.
   */
  @NotNull
  public static GlobalSearchScope filesWithoutLibrariesScope(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, false, false);
  }

  @NotNull
  public static GlobalSearchScope filesWithLibrariesScope(@NotNull Project project, @NotNull Collection<VirtualFile> files) {
    return filesWithLibrariesScope(project, files, false);
  }

  /**
   * @since 2017.3
   */
  @NotNull
  public static GlobalSearchScope filesWithLibrariesScope(@NotNull Project project, @NotNull Collection<VirtualFile> files,
                                                          boolean searchOutsideRootModel) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, true, searchOutsideRootModel);
  }

  /**
   * Please consider using {@link this#filesWithLibrariesScope} or {@link this#filesWithoutLibrariesScope} for optimization
   */
  @NotNull
  public static GlobalSearchScope filesScope(@NotNull Project project, @NotNull Collection<VirtualFile> files, @Nullable final String displayName) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return files.size() == 1? fileScope(project, files.iterator().next(), displayName) : new FilesScope(project, files) {
      @NotNull
      @Override
      public String getDisplayName() {
        return displayName == null ? super.getDisplayName() : displayName;
      }
    };
  }

  private static class IntersectionScope extends GlobalSearchScope {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;
    private final String myDisplayName;

    private IntersectionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2, String displayName) {
      super(scope1.getProject() == null ? scope2.getProject() : scope1.getProject());
      myScope1 = scope1;
      myScope2 = scope2;
      myDisplayName = displayName;
    }

    @NotNull
    @Override
    public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
      return containsScope(scope) ? this : new IntersectionScope(this, scope, null);
    }

    private boolean containsScope(@NotNull GlobalSearchScope scope) {
      if (myScope1.equals(scope) || myScope2.equals(scope) || equals(scope)) return true;
      if (myScope1 instanceof IntersectionScope && ((IntersectionScope)myScope1).containsScope(scope)) return true;
      if (myScope2 instanceof IntersectionScope && ((IntersectionScope)myScope2).containsScope(scope)) return true;
      return false;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      if (myDisplayName == null) {
        return PsiBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
      }
      return myDisplayName;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return myScope1.contains(file) && myScope2.contains(file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      int res1 = myScope1.compare(file1, file2);
      int res2 = myScope2.compare(file1, file2);

      if (res1 == 0) return res2;
      if (res2 == 0) return res1;

      res1 /= Math.abs(res1);
      res2 /= Math.abs(res2);
      if (res1 == res2) return res1;

      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return myScope1.isSearchInModuleContent(aModule) && myScope2.isSearchInModuleContent(aModule);
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module aModule, final boolean testSources) {
      return myScope1.isSearchInModuleContent(aModule, testSources) && myScope2.isSearchInModuleContent(aModule, testSources);
    }

    @Override
    public boolean isSearchInLibraries() {
      return myScope1.isSearchInLibraries() && myScope2.isSearchInLibraries();
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return myScope1.isSearchOutsideRootModel() && myScope2.isSearchOutsideRootModel();
    }

    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return ContainerUtil.intersection(myScope1.getUnloadedModulesBelongingToScope(), myScope2.getUnloadedModulesBelongingToScope());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IntersectionScope)) return false;

      IntersectionScope that = (IntersectionScope)o;

      return myScope1.equals(that.myScope1) && myScope2.equals(that.myScope2);
    }

    @Override
    public int hashCode() {
      return 31 * myScope1.hashCode() + myScope2.hashCode();
    }

    @NonNls
    @Override
    public String toString() {
      return "Intersection: (" + myScope1 + ", " + myScope2 + ")";
    }
  }

  private static class UnionScope extends GlobalSearchScope {
    private final GlobalSearchScope[] myScopes;
    private final int myNestingLevel;

    private UnionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2) {
      this(new GlobalSearchScope[]{scope1, scope2});
    }

    private UnionScope(@NotNull GlobalSearchScope[] scopes) {
      super(ContainerUtil.getFirstItem(ContainerUtil.mapNotNull(scopes, GlobalSearchScope::getProject), null));
      if (scopes.length <= 1) throw new IllegalArgumentException("Too few scopes: "+ Arrays.asList(scopes));
      myScopes = scopes;
      final int[] nested = {0};
      ContainerUtil.process(scopes, new Processor<GlobalSearchScope>() {
        @Override
        public boolean process(GlobalSearchScope scope) {
          nested[0] = Math.max(nested[0], scope instanceof UnionScope ? ((UnionScope)scope).myNestingLevel : 0);
          return true;
        }
      });
      myNestingLevel = 1 + nested[0];
      if (myNestingLevel > 1000) {
        throw new IllegalStateException("Too many scopes combined: " + myNestingLevel + StringUtil.last(toString(), 500, true));
      }
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return PsiBundle.message("psi.search.scope.union", myScopes[0].getDisplayName(), myScopes[1].getDisplayName());
    }

    @Override
    public boolean contains(@NotNull final VirtualFile file) {
      return ContainerUtil.find(myScopes, scope -> scope.contains(file)) != null;
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return ContainerUtil.find(myScopes, GlobalSearchScope::isSearchOutsideRootModel) != null;
    }

    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      Set<UnloadedModuleDescription> result = new LinkedHashSet<>();
      for (GlobalSearchScope scope : myScopes) {
        result.addAll(scope.getUnloadedModulesBelongingToScope());
      }
      return result;
    }

    @Override
    public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
      final int[] result = {0};
      ContainerUtil.process(myScopes, scope -> {
        int res1 = scope.contains(file1) && scope.contains(file2) ? scope.compare(file1, file2) : 0;
        if (result[0] == 0) {
          result[0] = res1;
          return true;
        }
        if (result[0] > 0 != res1 > 0) {
          result[0] = 0;
          return false;
        }
        return true;
      });
      return result[0];
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module module) {
      return ContainerUtil.find(myScopes, scope -> scope.isSearchInModuleContent(module)) != null;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull final Module module, final boolean testSources) {
      return ContainerUtil.find(myScopes, scope -> scope.isSearchInModuleContent(module, testSources)) != null;
    }

    @Override
    public boolean isSearchInLibraries() {
      return ContainerUtil.find(myScopes, GlobalSearchScope::isSearchInLibraries) != null;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof UnionScope)) return false;

      UnionScope that = (UnionScope)o;

      return new HashSet<>(Arrays.asList(myScopes)).equals(new HashSet<>(Arrays.asList(that.myScopes)));
    }

    @Override
    public int hashCode() {
      return Arrays.hashCode(myScopes);
    }

    @NonNls
    @Override
    public String toString() {
      return "Union: (" + StringUtil.join(Arrays.asList(myScopes), ",") + ")";
    }

    @NotNull
    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (scope instanceof UnionScope) {
        GlobalSearchScope[] newScopes = ArrayUtil.mergeArrays(myScopes, ((UnionScope)scope).myScopes);
        return new UnionScope(newScopes);
      }
      return super.uniteWith(scope);
    }
  }

  @NotNull
  @Contract(pure=true)
  public static GlobalSearchScope getScopeRestrictedByFileTypes(@NotNull GlobalSearchScope scope, @NotNull FileType... fileTypes) {
    if (scope == EMPTY_SCOPE) {
      return EMPTY_SCOPE;
    }
    if (fileTypes.length == 0) throw new IllegalArgumentException("empty fileTypes");
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  private static class FileTypeRestrictionScope extends DelegatingGlobalSearchScope {
    private final FileType[] myFileTypes;

    private FileTypeRestrictionScope(@NotNull GlobalSearchScope scope, @NotNull FileType[] fileTypes) {
      super(scope);
      myFileTypes = fileTypes;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      if (!super.contains(file)) return false;

      final FileType fileType = file.getFileType();
      for (FileType otherFileType : myFileTypes) {
        if (fileType.equals(otherFileType)) return true;
      }

      return false;
    }

    @NotNull
    @Override
    public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
      if (scope instanceof FileTypeRestrictionScope) {
        FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
        if (restrict.myBaseScope == myBaseScope) {
          List<FileType> intersection = new ArrayList<>(Arrays.asList(restrict.myFileTypes));
          intersection.retainAll(Arrays.asList(myFileTypes));
          return new FileTypeRestrictionScope(myBaseScope, intersection.toArray(new FileType[intersection.size()]));
        }
      }
      return super.intersectWith(scope);
    }

    @NotNull
    @Override
    public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
      if (scope instanceof FileTypeRestrictionScope) {
        FileTypeRestrictionScope restrict = (FileTypeRestrictionScope)scope;
        if (restrict.myBaseScope == myBaseScope) {
          return new FileTypeRestrictionScope(myBaseScope, ArrayUtil.mergeArrays(myFileTypes, restrict.myFileTypes));
        }
      }
      return super.uniteWith(scope);
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof FileTypeRestrictionScope)) return false;
      if (!super.equals(o)) return false;

      FileTypeRestrictionScope that = (FileTypeRestrictionScope)o;

      return Arrays.equals(myFileTypes, that.myFileTypes);
    }

    @Override
    public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + Arrays.hashCode(myFileTypes);
      return result;
    }

    @Override
    public String toString() {
      return "(restricted by file types: "+Arrays.asList(myFileTypes)+" in "+ myBaseScope + ")";
    }
  }

  private static class EmptyScope extends GlobalSearchScope {
    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return false;
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
      return false;
    }

    @Override
    @NotNull
    public GlobalSearchScope intersectWith(@NotNull final GlobalSearchScope scope) {
      return this;
    }

    @Override
    @NotNull
    public GlobalSearchScope uniteWith(@NotNull final GlobalSearchScope scope) {
      return scope;
    }

    @Override
    public String toString() {
      return "EMPTY";
    }
  }

  public static final GlobalSearchScope EMPTY_SCOPE = new EmptyScope();

  private static class FileScope extends GlobalSearchScope implements Iterable<VirtualFile> {
    private final VirtualFile myVirtualFile; // files can be out of project roots
    private final Module myModule;
    private final boolean mySearchOutsideContent;

    private FileScope(@NotNull Project project, @Nullable VirtualFile virtualFile) {
      super(project);
      myVirtualFile = virtualFile;
      final FileIndexFacade facade = FileIndexFacade.getInstance(project);
      myModule = virtualFile == null || project.isDefault() ? null : facade.getModuleForFile(virtualFile);
      mySearchOutsideContent = virtualFile != null && myModule == null && !facade.isInLibraryClasses(virtualFile) && !facade.isInLibrarySource(virtualFile);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return Comparing.equal(myVirtualFile, file);
    }

    @Override
    public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return aModule == myModule;
    }

    @Override
    public boolean isSearchInLibraries() {
      return myModule == null;
    }

    @Override
    public String toString() {
      return "File :"+myVirtualFile;
    }

    @NotNull
    @Override
    public Iterator<VirtualFile> iterator() {
      return Collections.singletonList(myVirtualFile).iterator();
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return mySearchOutsideContent;
    }
  }

  public static class FilesScope extends GlobalSearchScope implements Iterable<VirtualFile> {
    private final Collection<VirtualFile> myFiles;
    private final boolean mySearchOutsideRootModel;
    private volatile Boolean myHasFilesOutOfProjectRoots;

    /**
     * @deprecated use {@link GlobalSearchScope#filesScope(Project, Collection)}
     */
    public FilesScope(@Nullable Project project, @NotNull Collection<VirtualFile> files) {
      this(project, files, null, false);
    }

    // Optimization
    private FilesScope(@Nullable  Project project, @NotNull Collection<VirtualFile> files, @Nullable Boolean hasFilesOutOfProjectRoots,
                       boolean searchOutsideRootModel) {
      super(project);
      myFiles = files;
      mySearchOutsideRootModel = searchOutsideRootModel;
      myHasFilesOutOfProjectRoots = hasFilesOutOfProjectRoots;
    }

    @Override
    public boolean contains(@NotNull final VirtualFile file) {
      return myFiles.contains(file);
    }

    @Override
    public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
      return 0;
    }

    @Override
    public boolean isSearchInModuleContent(@NotNull Module aModule) {
      return true;
    }

    @Override
    public boolean isSearchInLibraries() {
      return hasFilesOutOfProjectRoots();
    }

    @Override
    public boolean equals(Object o) {
      return this == o || o instanceof FilesScope && myFiles.equals(((FilesScope)o).myFiles);
    }

    @Override
    public int hashCode() {
      return myFiles.hashCode();
    }

    private boolean hasFilesOutOfProjectRoots() {
      Boolean result = myHasFilesOutOfProjectRoots;
      if (result == null) {
        Project project = getProject();
        myHasFilesOutOfProjectRoots = result =
          project != null && !project.isDefault() &&
          myFiles.stream().anyMatch(file -> FileIndexFacade.getInstance(project).getModuleForFile(file) == null);
      }
      return result;
    }

    @Override
    public String toString() {
      List<VirtualFile> files = myFiles.size() <= 20 ? new ArrayList<>(myFiles) : new ArrayList<>(myFiles).subList(0, 20);
      return "Files: ("+ files +"); search in libraries: " + (myHasFilesOutOfProjectRoots != null ? myHasFilesOutOfProjectRoots : "unknown");
    }

    @NotNull
    @Override
    public Iterator<VirtualFile> iterator() {
      return myFiles.iterator();
    }

    @Override
    public boolean isSearchOutsideRootModel() {
      return mySearchOutsideRootModel;
    }
  }
}
