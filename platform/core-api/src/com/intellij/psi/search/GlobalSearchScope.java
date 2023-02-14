// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.search;

import com.intellij.core.CoreBundle;
import com.intellij.model.ModelBranch;
import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.FileIndexFacade;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.impl.IntersectionFileEnumeration;
import com.intellij.psi.search.impl.UnionFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumeration;
import com.intellij.psi.search.impl.VirtualFileEnumerationAware;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ObjectUtils;
import com.intellij.util.SmartList;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.*;

import java.util.*;
import java.util.function.Supplier;

/**
 * Project model-aware search scope.
 *
 * @see com.intellij.psi.search.GlobalSearchScopes
 */
public abstract class GlobalSearchScope extends SearchScope implements ProjectAwareFileFilter {
  public static final GlobalSearchScope[] EMPTY_ARRAY = new GlobalSearchScope[0];
  @Nullable private final Project myProject;

  protected GlobalSearchScope(@Nullable Project project) {
    myProject = project;
  }

  protected GlobalSearchScope() {
    this(null);
  }

  @ApiStatus.NonExtendable
  @Override
  public @Nullable Project getProject() {
    return myProject;
  }

  /**
   * @return <ul>
   * <li>a positive integer (e.g. +1), if file1 is located in the classpath before file2</li>
   * <li>a negative integer (e.e -1), if file1 is located in the classpath after file2</li>
   * <li>zero - otherwise or when the files are not comparable</li>
   * </ul>
   */
  public int compare(@NotNull VirtualFile file1, @NotNull VirtualFile file2) {
    return 0;
  }

  // optimization methods:

  public abstract boolean isSearchInModuleContent(@NotNull Module aModule);

  public boolean isSearchInModuleContent(@NotNull Module aModule, boolean testSources) {
    return isSearchInModuleContent(aModule);
  }

  @Override
  public final boolean accept(@NotNull VirtualFile file) {
    return contains(file);
  }

  public abstract boolean isSearchInLibraries();

  public boolean isForceSearchingInLibrarySources() {
    return false;
  }

  /**
   * Returns descriptions of unloaded modules whose content might be included in this scope if they had been loaded. Actually, search in
   * unloaded modules isn't performed, so this method is used to determine whether a warning about possible missing results should be shown.
   */
  @NotNull
  public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return Collections.emptySet();
  }

  /**
   * @return a set of model branches whose copied files this scope might contain
   */
  @ApiStatus.Experimental
  public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
    return Collections.emptySet();
  }

  @NotNull
  @Contract(pure = true)
  public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return this;
    if (scope instanceof IntersectionScope && ((IntersectionScope)scope).containsScope(this)) {
      return scope;
    }
    return new IntersectionScope(this, scope);
  }

  @NotNull
  @Override
  @Contract(pure = true)
  public SearchScope intersectWith(@NotNull SearchScope scope2) {
    if (scope2 instanceof LocalSearchScope) {
      return intersectWith((LocalSearchScope)scope2);
    }
    return intersectWith((GlobalSearchScope)scope2);
  }

  @NotNull
  @Contract(pure = true)
  public LocalSearchScope intersectWith(@NotNull LocalSearchScope localScope2) {
    PsiElement[] elements2 = localScope2.getScope();
    List<PsiElement> result = new ArrayList<>(elements2.length);
    for (final PsiElement element2 : elements2) {
      if (PsiSearchScopeUtil.isInScope(this, element2)) {
        result.add(element2);
      }
    }
    return result.isEmpty() ? LocalSearchScope.EMPTY : new LocalSearchScope(result.toArray(PsiElement.EMPTY_ARRAY), null, localScope2.isIgnoreInjectedPsi());
  }

  @Override
  @NotNull
  @Contract(pure = true)
  public GlobalSearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof GlobalSearchScope) return uniteWith((GlobalSearchScope)scope);
    return union((LocalSearchScope)scope);
  }

  @NotNull
  @Contract(pure = true)
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
      public boolean isSearchInLibraries() {
        return GlobalSearchScope.this.isSearchInLibraries();
      }

      @NotNull
      @Override
      public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
        return GlobalSearchScope.this.getUnloadedModulesBelongingToScope();
      }

      @Override
      public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
        return GlobalSearchScope.this.getModelBranchesAffectingScope();
      }

      @NonNls
      @Override
      public String toString() {
        return "UnionToLocal: (" + GlobalSearchScope.this + ", " + scope + ")";
      }
    };
  }

  @NotNull
  @Contract(pure = true)
  public GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    return UnionScope.create(new GlobalSearchScope[]{this, scope});
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope union(@NotNull Collection<? extends GlobalSearchScope> scopes) {
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException("Empty scope collection");
    }
    if (scopes.size() == 1) {
      return scopes.iterator().next();
    }
    return UnionScope.create(scopes.toArray(EMPTY_ARRAY));
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope union(GlobalSearchScope @NotNull [] scopes) {
    if (scopes.length == 0) {
      throw new IllegalArgumentException("Empty scope array");
    }
    if (scopes.length == 1) {
      return scopes[0];
    }
    return UnionScope.create(scopes);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope allScope(@NotNull Project project) {
    return ProjectScope.getAllScope(project);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope projectScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope everythingScope(@NotNull Project project) {
    return ProjectScope.getEverythingScope(project);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope notScope(@NotNull final GlobalSearchScope scope) {
    return new NotScope(scope);
  }

  private static final class NotScope extends DelegatingGlobalSearchScope {
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
    public String toString() {
      return "NOT: (" + myBaseScope + ")";
    }
  }

  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources and tests, excluding libraries and dependencies.
   */
  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleScope(@NotNull Module module) {
    return module.getModuleScope();
  }

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources, tests, and libraries, excluding dependencies.
   */
  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleWithLibrariesScope(@NotNull Module module) {
    return module.getModuleWithLibrariesScope();
  }

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources, tests, and dependencies, excluding libraries.
   */
  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleRuntimeScope(@NotNull Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope moduleTestsWithDependentsScope(@NotNull Module module) {
    return module.getModuleTestsWithDependentsScope();
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope fileScope(@NotNull PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    return new FileScope(psiFile.getProject(), virtualFile != null ? BackedVirtualFile.getOriginFileIfBacked(virtualFile) : null, null);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope fileScope(@NotNull Project project, final VirtualFile virtualFile) {
    return fileScope(project, virtualFile, null);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope fileScope(@NotNull Project project, @Nullable VirtualFile virtualFile, @Nullable final @Nls String displayName) {
    return new FileScope(project, virtualFile, displayName);
  }

  /**
   * Please consider using {@link #filesWithLibrariesScope} or {@link #filesWithoutLibrariesScope} for optimization
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope filesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files) {
    return filesScope(project, files, null);
  }

  /**
   * Lazy files scope: can be created (e.g., to display in UI) but the files won't be loaded until it's actually used
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope filesScope(@NotNull Project project, @NotNull Supplier<? extends Collection<? extends VirtualFile>> files) {
    return new LazyFilesScope(project, files);
  }

  /**
   * Optimization. By default, FilesScope makes a decision about searching in libraries by checking that
   * at least one file is placed out of module roots.
   * So if you're sure about file placement, you can explicitly say FilesScope whether
   * it should include libraries or not in order to avoid checking each file.
   * Also, if you have a lot of files, it might be faster to always search in libraries.
   */
  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope filesWithoutLibrariesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, false);
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope filesWithLibrariesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, true);
  }

  /**
   * Please consider using {@link this#filesWithLibrariesScope} or {@link this#filesWithoutLibrariesScope} for optimization
   */
  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope filesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files, @Nullable final @Nls String displayName) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return files.size() == 1? fileScope(project, files.iterator().next(), displayName) : new FilesScope(project, files) {
      @NotNull
      @Override
      public String getDisplayName() {
        return displayName == null ? super.getDisplayName() : displayName;
      }
    };
  }

  private static final class IntersectionScope extends GlobalSearchScope implements VirtualFileEnumerationAware {
    private final GlobalSearchScope myScope1;
    private final GlobalSearchScope myScope2;

    private IntersectionScope(@NotNull GlobalSearchScope scope1, @NotNull GlobalSearchScope scope2) {
      super(scope1.getProject() == null ? scope2.getProject() : scope1.getProject());
      myScope1 = scope1;
      myScope2 = scope2;
    }

    @NotNull
    @Override
    public GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
      return containsScope(scope) ? this : new IntersectionScope(this, scope);
    }

    private boolean containsScope(@NotNull GlobalSearchScope scope) {
      if (myScope1.equals(scope) || myScope2.equals(scope) || equals(scope)) return true;
      if (myScope1 instanceof IntersectionScope && ((IntersectionScope)myScope1).containsScope(scope)) return true;
      return myScope2 instanceof IntersectionScope && ((IntersectionScope)myScope2).containsScope(scope);
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return CoreBundle.message("psi.search.scope.intersection", myScope1.getDisplayName(), myScope2.getDisplayName());
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

      if (res1 > 0 == res2 > 0) return res1;

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

    @NotNull
    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      return ContainerUtil.intersection(myScope1.getUnloadedModulesBelongingToScope(), myScope2.getUnloadedModulesBelongingToScope());
    }

    @Override
    public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
      return ContainerUtil.intersection(myScope1.getModelBranchesAffectingScope(), myScope2.getModelBranchesAffectingScope());
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof IntersectionScope)) return false;

      IntersectionScope that = (IntersectionScope)o;

      return myScope1.equals(that.myScope1) && myScope2.equals(that.myScope2);
    }

    @Override
    public int calcHashCode() {
      return 31 * myScope1.hashCode() + myScope2.hashCode();
    }

    @NonNls
    @Override
    public String toString() {
      return "Intersection: (" + myScope1 + ", " + myScope2 + ")";
    }

    @Override
    public @Nullable VirtualFileEnumeration extractFileEnumeration() {
      VirtualFileEnumeration fileEnumeration1 = VirtualFileEnumeration.extract(myScope1);
      VirtualFileEnumeration fileEnumeration2 = VirtualFileEnumeration.extract(myScope2);
      if (fileEnumeration1 == null) return fileEnumeration2;
      if (fileEnumeration2 == null) return fileEnumeration1;
      return new IntersectionFileEnumeration(Arrays.asList(fileEnumeration1, fileEnumeration2));
    }
  }

  private static final class UnionScope extends GlobalSearchScope implements VirtualFileEnumerationAware {
    private final GlobalSearchScope @NotNull [] myScopes;

    @Override
    public @Nullable VirtualFileEnumeration extractFileEnumeration() {
      Collection<VirtualFileEnumeration> fileEnumerations = new SmartList<>();
      for (GlobalSearchScope scope : myScopes) {
        VirtualFileEnumeration fileEnumeration = VirtualFileEnumeration.extract(scope);
        if (fileEnumeration == null) {
          return null;
        }
        fileEnumerations.add(fileEnumeration);
      }
      return new UnionFileEnumeration(fileEnumerations);
    }

    @NotNull
    static GlobalSearchScope create(GlobalSearchScope @NotNull [] scopes) {
      if (scopes.length == 2) {
        GlobalSearchScope unionScope = tryCreateUnionFor2Scopes(scopes);
        if (unionScope != null) return unionScope;
      }
      Set<GlobalSearchScope> result = new HashSet<>(scopes.length);
      Project project = null;
      for (GlobalSearchScope scope : scopes) {
        if (scope == EMPTY_SCOPE) continue;
        Project scopeProject = scope.getProject();
        if (scopeProject != null) project = scopeProject;
        if (scope instanceof UnionScope) {
          ContainerUtil.addAll(result, ((UnionScope)scope).myScopes);
        }
        else {
          result.add(scope);
        }
      }
      if (result.isEmpty()) return EMPTY_SCOPE;
      if (result.size() == 1) return result.iterator().next();
      return new UnionScope(project, result.toArray(EMPTY_ARRAY));
    }

    @Nullable
    private static GlobalSearchScope tryCreateUnionFor2Scopes(GlobalSearchScope @NotNull [] scopes) {
      assert scopes.length == 2;
      GlobalSearchScope scope0 = scopes[0];
      GlobalSearchScope scope1 = scopes[1];
      if (scope0 == EMPTY_SCOPE) return scope1;
      if (scope1 == EMPTY_SCOPE) return scope0;
      if (scope0 instanceof UnionScope && scope1 instanceof UnionScope) return null;
      Project project = ObjectUtils.chooseNotNull(scope0.getProject(), scope1.getProject());

      if (scope0 instanceof UnionScope) {
        return unionWithUnionScope(scope0, scope1, project);
      }

      if (scope1 instanceof UnionScope) {
        return unionWithUnionScope(scope1, scope0, project);
      }

      return new UnionScope(project, scopes);
    }

    @NotNull
    private static GlobalSearchScope unionWithUnionScope(GlobalSearchScope scope0, GlobalSearchScope scope1, Project project) {
      GlobalSearchScope[] scopes0 = ((UnionScope)scope0).myScopes;
      if (ArrayUtil.contains(scope1, scopes0)) {
        return scope0;
      }
      else {
        return new UnionScope(project, ArrayUtil.append(scopes0, scope1));
      }
    }

    private UnionScope(Project project, GlobalSearchScope @NotNull [] scopes) {
      super(project);
      myScopes = scopes;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return CoreBundle.message("psi.search.scope.union", myScopes[0].getDisplayName(), myScopes[1].getDisplayName());
    }

    @Override
    public boolean contains(@NotNull final VirtualFile file) {
      return ContainerUtil.find(myScopes, scope -> scope.contains(file)) != null;
    }

    @NotNull
    @Override
    public Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
      Set<UnloadedModuleDescription> result = new LinkedHashSet<>();
      for (GlobalSearchScope scope : myScopes) {
        result.addAll(scope.getUnloadedModulesBelongingToScope());
      }
      return result;
    }

    @Override
    public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
      Set<ModelBranch> result = new LinkedHashSet<>();
      for (GlobalSearchScope scope : myScopes) {
        result.addAll(scope.getModelBranchesAffectingScope());
      }
      return result;
    }

    @Override
    public int compare(@NotNull final VirtualFile file1, @NotNull final VirtualFile file2) {
      final int[] result = {0};
      ContainerUtil.process(myScopes, scope -> {
        // ignore irrelevant scopes - they don't know anything about the files
        if (!scope.contains(file1) || !scope.contains(file2)) return true;
        int cmp = scope.compare(file1, file2);
        if (result[0] == 0) {
          result[0] = cmp;
          return true;
        }
        if (cmp == 0) {
          return true;
        }
        if (result[0] > 0 == cmp > 0) {
          return true;
        }
        // scopes disagree about the order - abort the voting
        result[0] = 0;
        return false;
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

      return ContainerUtil.set(myScopes).equals(ContainerUtil.set(that.myScopes));
    }

    @Override
    public int calcHashCode() {
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
        return create(newScopes);
      }
      return super.uniteWith(scope);
    }
  }

  @NotNull
  @Contract(pure = true)
  public static GlobalSearchScope getScopeRestrictedByFileTypes(@NotNull GlobalSearchScope scope, FileType @NotNull ... fileTypes) {
    if (scope == EMPTY_SCOPE) {
      return EMPTY_SCOPE;
    }
    if (fileTypes.length == 0) throw new IllegalArgumentException("empty fileTypes");
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  private static final class FileTypeRestrictionScope extends DelegatingGlobalSearchScope implements VirtualFileEnumerationAware {
    private final FileType[] myFileTypes;

    private FileTypeRestrictionScope(@NotNull GlobalSearchScope scope, FileType @NotNull [] fileTypes) {
      super(scope);
      myFileTypes = fileTypes;
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      if (!super.contains(file)) return false;

      for (FileType otherFileType : myFileTypes) {
        if (FileTypeRegistry.getInstance().isFileOfType(file, otherFileType)) return true;
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
          return new FileTypeRestrictionScope(myBaseScope, intersection.toArray(FileType.EMPTY_ARRAY));
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
    public int calcHashCode() {
      int result = super.calcHashCode();
      result = 31 * result + Arrays.hashCode(myFileTypes);
      return result;
    }

    @Override
    public String toString() {
      return "Restricted by file types: " + Arrays.asList(myFileTypes) + " in (" + myBaseScope + ")";
    }

    @Override
    public @Nullable VirtualFileEnumeration extractFileEnumeration() {
      return myBaseScope instanceof VirtualFileEnumeration ? (VirtualFileEnumeration)myBaseScope : null;
    }
  }

  private static class EmptyScope extends GlobalSearchScope {
    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return false;
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

  private static final class FileScope extends GlobalSearchScope implements VirtualFileEnumeration {
    private final VirtualFile myVirtualFile; // files can be out of project roots
    @Nullable private final @Nls String myDisplayName;
    private final Module myModule;

    private FileScope(@NotNull Project project, @Nullable VirtualFile virtualFile, @Nullable @Nls String displayName) {
      super(project);
      myVirtualFile = virtualFile;
      myDisplayName = displayName;
      FileIndexFacade facade = project.isDefault() ? null : FileIndexFacade.getInstance(project);
      myModule = virtualFile == null || facade == null ? null : facade.getModuleForFile(virtualFile);
    }

    @Override
    public @NotNull Collection<VirtualFile> getFilesIfCollection() {
      return Collections.singleton(myVirtualFile);
    }

    @Override
    public boolean contains(@NotNull VirtualFile file) {
      return Comparing.equal(myVirtualFile, file);
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
      return "File: " + myVirtualFile;
    }

    @NotNull
    @Override
    public String getDisplayName() {
      return myDisplayName != null ? myDisplayName : super.getDisplayName();
    }

    @Override
    public @NotNull Collection<ModelBranch> getModelBranchesAffectingScope() {
      return myVirtualFile == null
             ? Collections.emptyList()
             : ContainerUtil.createMaybeSingletonList(ModelBranch.getFileBranch(myVirtualFile));
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || o.getClass() != getClass()) return false;
      FileScope files = (FileScope)o;
      return Objects.equals(myVirtualFile, files.myVirtualFile) &&
             Objects.equals(myDisplayName, files.myDisplayName) &&
             Objects.equals(myModule, files.myModule);
    }

    @Override
    protected int calcHashCode() {
      return Objects.hash(myVirtualFile, myModule, myDisplayName);
    }

    @Override
    public boolean contains(int fileId) {
      return myVirtualFile instanceof VirtualFileWithId && ((VirtualFileWithId)myVirtualFile).getId() == fileId;
    }

    @Override
    public int @NotNull [] asArray() {
      return myVirtualFile instanceof VirtualFileWithId
             ? new int[]{((VirtualFileWithId)myVirtualFile).getId()}
             : ArrayUtil.EMPTY_INT_ARRAY;
    }
  }


  private abstract static class AbstractFilesScope extends GlobalSearchScope implements VirtualFileEnumeration {
    volatile Boolean myHasFilesOutOfProjectRoots;

    // Optimization
    AbstractFilesScope(@Nullable Project project, @Nullable Boolean hasFilesOutOfProjectRoots) {
      super(project);
      myHasFilesOutOfProjectRoots = hasFilesOutOfProjectRoots;
    }

    @Override
    public @Nullable Collection<VirtualFile> getFilesIfCollection() {
      return getFiles();
    }

    abstract @NotNull VirtualFileSet getFiles();

    @Override
    public boolean contains(@NotNull final VirtualFile file) {
      return getFiles().contains(file);
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
      return this == o || o instanceof AbstractFilesScope && getFiles().equals(((AbstractFilesScope)o).getFiles());
    }

    @Override
    public int calcHashCode() {
      return getFiles().hashCode();
    }

    private boolean hasFilesOutOfProjectRoots() {
      Boolean result = myHasFilesOutOfProjectRoots;
      if (result == null) {
        Project project = getProject();
        myHasFilesOutOfProjectRoots = result =
          project != null && !project.isDefault() &&
          ContainerUtil.find(getFiles(), file -> FileIndexFacade.getInstance(project).getModuleForFile(file) != null) == null;
      }
      return result;
    }

    @Override
    public boolean contains(int fileId) {
      return ((VirtualFileSetEx)getFiles()).containsId(fileId);
    }

    @Override
    public int @NotNull [] asArray() {
      return ((VirtualFileSetEx)getFiles()).onlyInternalFileIds();
    }
  }

  public static class FilesScope extends AbstractFilesScope {
    private final VirtualFileSet myFiles;

    private FilesScope(@Nullable Project project, @NotNull Collection<? extends VirtualFile> files) {
      this(project, files, null);
    }

    // Optimization
    private FilesScope(@Nullable Project project, @NotNull Collection<? extends VirtualFile> files, @Nullable Boolean hasFilesOutOfProjectRoots) {
      super(project, hasFilesOutOfProjectRoots);
      myFiles = VfsUtilCore.createCompactVirtualFileSet(files);
      myFiles.freeze();
    }

    @Override
    public @NotNull VirtualFileSet getFiles() {
      return myFiles;
    }

    @Override
    public String toString() {
      return "Files: [" +
             StringUtil.join(myFiles, ", ") +
             "]; search in libraries: " +
             (myHasFilesOutOfProjectRoots != null ? myHasFilesOutOfProjectRoots : "unknown");
    }
  }

  private static class LazyFilesScope extends AbstractFilesScope {
    private volatile VirtualFileSet myFiles;
    private @NotNull Supplier<? extends Collection<? extends VirtualFile>> myFilesSupplier;

    private LazyFilesScope(@Nullable Project project, @NotNull Supplier<? extends Collection<? extends VirtualFile>> files) {
      super(project, null);
      myFilesSupplier = files;
    }

    @Override
    public @NotNull VirtualFileSet getFiles() {
      if (myFiles == null) {
        synchronized (this) {
          if (myFiles == null) {
            VirtualFileSet fileSet = VfsUtilCore.createCompactVirtualFileSet(myFilesSupplier.get());
            fileSet.freeze();
            myFilesSupplier = null;
            myFiles = fileSet;
          }
        }
      }
      return myFiles;
    }

    @Override
    public String toString() {
      return "Files: [" +
             (myFiles == null ? "(not loaded yet)" : StringUtil.join(myFiles, ", ")) +
             "]; search in libraries: " +
             (myHasFilesOutOfProjectRoots != null ? myHasFilesOutOfProjectRoots : "unknown");
    }
  }
}
