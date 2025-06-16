// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.search;

import com.intellij.notebook.editor.BackedVirtualFile;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.UnloadedModuleDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileSet;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.*;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * Project model-aware search scope.
 *
 * @see com.intellij.psi.search.GlobalSearchScopes
 */
public abstract class GlobalSearchScope extends SearchScope implements ProjectAwareFileFilter {
  public static final GlobalSearchScope[] EMPTY_ARRAY = new GlobalSearchScope[0];
  private final Project myProject;
  private static final Key<Boolean> USE_WEAK_FILE_SCOPE = Key.create("virtual.file.use.weak.scope");

  protected GlobalSearchScope(@Nullable Project project) {
    myProject = project;
  }

  protected GlobalSearchScope() {
    this(null);
  }

  @ApiStatus.NonExtendable
  @Override
  public Project getProject() {
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
  public @Unmodifiable @NotNull Collection<UnloadedModuleDescription> getUnloadedModulesBelongingToScope() {
    return Collections.emptySet();
  }

  @Contract(pure = true)
  public @NotNull GlobalSearchScope intersectWith(@NotNull GlobalSearchScope scope) {
    return defaultIntersectWith(scope);
  }

  @ApiStatus.Internal
  @Contract(pure = true)
  protected final @NotNull GlobalSearchScope defaultIntersectWith(@NotNull GlobalSearchScope scope) {
    if (scope == this) return this;
    if (scope instanceof IntersectionScope && ((IntersectionScope)scope).containsScope(this)) {
      return scope;
    }
    return new IntersectionScope(this, scope);
  }

  @Override
  @Contract(pure = true)
  public @NotNull SearchScope intersectWith(@NotNull SearchScope scope2) {
    if (scope2 instanceof LocalSearchScope) {
      return intersectWith((LocalSearchScope)scope2);
    }
    return intersectWith((GlobalSearchScope)scope2);
  }

  @Contract(pure = true)
  public @NotNull LocalSearchScope intersectWith(@NotNull LocalSearchScope localScope2) {
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
  @Contract(pure = true)
  public @NotNull GlobalSearchScope union(@NotNull SearchScope scope) {
    if (scope instanceof GlobalSearchScope) return uniteWith((GlobalSearchScope)scope);
    return union((LocalSearchScope)scope);
  }

  @Contract(pure = true)
  public @NotNull GlobalSearchScope union(final @NotNull LocalSearchScope scope) {
    PsiElement[] localScopeElements = scope.getScope();
    if (localScopeElements.length == 0) {
      return this;
    }
    return new GlobalAndLocalUnionScope(this, scope, localScopeElements[0].getProject());
  }

  @Contract(pure = true)
  public @NotNull GlobalSearchScope uniteWith(@NotNull GlobalSearchScope scope) {
    return UnionScope.create(new GlobalSearchScope[]{this, scope});
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope union(@NotNull Collection<? extends GlobalSearchScope> scopes) {
    if (scopes.isEmpty()) {
      throw new IllegalArgumentException("Empty scope collection");
    }
    if (scopes.size() == 1) {
      return scopes.iterator().next();
    }
    return UnionScope.create(scopes.toArray(EMPTY_ARRAY));
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope union(GlobalSearchScope @NotNull [] scopes) {
    if (scopes.length == 0) {
      throw new IllegalArgumentException("Empty scope array");
    }
    if (scopes.length == 1) {
      return scopes[0];
    }
    return UnionScope.create(scopes);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope allScope(@NotNull Project project) {
    return ProjectScope.getAllScope(project);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope projectScope(@NotNull Project project) {
    return ProjectScope.getProjectScope(project);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope everythingScope(@NotNull Project project) {
    return ProjectScope.getEverythingScope(project);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope notScope(final @NotNull GlobalSearchScope scope) {
    return new NotScope(scope);
  }

  /**
   * Returns module scope including sources and tests, excluding libraries and dependencies.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources and tests, excluding libraries and dependencies.
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleScope(@NotNull Module module) {
    return module.getModuleScope();
  }

  /**
   * Returns module scope including sources, tests, and libraries, excluding dependencies.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources, tests, and libraries, excluding dependencies.
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleWithLibrariesScope(@NotNull Module module) {
    return module.getModuleWithLibrariesScope();
  }

  /**
   * Returns module scope including sources, tests, and dependencies, excluding libraries.
   *
   * @param module the module to get the scope.
   * @return the scope, including sources, tests, and dependencies, excluding libraries.
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleWithDependenciesScope(@NotNull Module module) {
    return module.getModuleWithDependenciesScope();
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleRuntimeScope(@NotNull Module module, final boolean includeTests) {
    return module.getModuleRuntimeScope(includeTests);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module) {
    return moduleWithDependenciesAndLibrariesScope(module, true);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleWithDependenciesAndLibrariesScope(@NotNull Module module, boolean includeTests) {
    return module.getModuleWithDependenciesAndLibrariesScope(includeTests);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleWithDependentsScope(@NotNull Module module) {
    return module.getModuleWithDependentsScope();
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope moduleTestsWithDependentsScope(@NotNull Module module) {
    return module.getModuleTestsWithDependentsScope();
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope fileScope(@NotNull PsiFile psiFile) {
    VirtualFile virtualFile = psiFile.getVirtualFile();
    return new FileScope(psiFile.getProject(), virtualFile != null ? BackedVirtualFile.getOriginFileIfBacked(virtualFile) : null, null);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope fileScope(@NotNull Project project, final @Nullable VirtualFile virtualFile) {
    return fileScope(project, virtualFile, null);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope fileScope(@NotNull Project project,
                                                     @Nullable VirtualFile virtualFile,
                                                     final @Nullable @Nls String displayName) {
    if (virtualFile != null && virtualFile.getUserData(USE_WEAK_FILE_SCOPE) == Boolean.TRUE) {
      return new FileWeakScope(project, virtualFile, displayName);
    }
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
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope filesWithoutLibrariesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, false);
  }

  @Contract(pure = true)
  public static @NotNull GlobalSearchScope filesWithLibrariesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files) {
    if (files.isEmpty()) return EMPTY_SCOPE;
    return new FilesScope(project, files, true);
  }

  /**
   * Please consider using {@link this#filesWithLibrariesScope} or {@link this#filesWithoutLibrariesScope} for optimization
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope filesScope(@NotNull Project project, @NotNull Collection<? extends VirtualFile> files, @Nullable @Nls String displayName) {
    if (files.isEmpty()) {
      return EMPTY_SCOPE;
    }
    if (files.size() == 1) {
      return fileScope(project, files.iterator().next(), displayName);
    }
    if (displayName != null) {
      return new FilesScopeWithDisplayName(project, files, displayName);
    }
    return new FilesScope(project, files, null);
  }

  /**
   * @see PsiSearchScopeUtil#restrictScopeTo(SearchScope, FileType...)
   */
  @Contract(pure = true)
  public static @NotNull GlobalSearchScope getScopeRestrictedByFileTypes(@NotNull GlobalSearchScope scope, FileType @NotNull ... fileTypes) {
    if (scope == EMPTY_SCOPE) {
      return EMPTY_SCOPE;
    }
    if (fileTypes.length == 0) throw new IllegalArgumentException("empty fileTypes");
    return new FileTypeRestrictionScope(scope, fileTypes);
  }

  /**
   * Marks a specified file to use a weak file scope. This is useful for modifying
   * the scoping mechanism of a file to ensure it is treated under weaker constraints
   * than the default resolve scope. It can be used to improve memory consumption.
   *
   * @param file the virtual file to be marked for weak scope; must not be null
   */
  @ApiStatus.Internal
  public static void markFileForWeakScope(@NotNull VirtualFile file) {
    file.putUserData(USE_WEAK_FILE_SCOPE, Boolean.TRUE);
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
    public @NotNull GlobalSearchScope intersectWith(final @NotNull GlobalSearchScope scope) {
      return this;
    }

    @Override
    public @NotNull GlobalSearchScope uniteWith(final @NotNull GlobalSearchScope scope) {
      return scope;
    }

    @Override
    public String toString() {
      return "EMPTY";
    }
  }

  public static final GlobalSearchScope EMPTY_SCOPE = new EmptyScope();

  public static class FilesScope extends AbstractFilesScope {
    private final VirtualFileSet myFiles;

    /** @param hasFilesOutOfProjectRoots optimization */
    @ApiStatus.Internal
    FilesScope(@Nullable Project project, @NotNull Collection<? extends VirtualFile> files, @Nullable Boolean hasFilesOutOfProjectRoots) {
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
}
