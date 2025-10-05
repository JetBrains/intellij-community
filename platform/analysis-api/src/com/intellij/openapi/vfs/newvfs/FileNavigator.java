// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.openapi.vfs.VFileProperty.SYMLINK;
import static com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem.LOG;

/**
 * Interface abstracts out walking through VirtualFile hierarchy.
 * Utilised by the {@link #navigate(NewVirtualFileSystem, String, FileNavigator)} method to resolve a String path to
 * a {@link VirtualFile}: implementations of this interface provide a way to 'move' up(=parent) and down(=child) the
 * hierarchy, while {@link #navigate(NewVirtualFileSystem, String, FileNavigator)} method scans the path string, and
 * uses the {@link FileNavigator} methods to 'interpret' path segments.
 */
@ApiStatus.Internal
public interface FileNavigator<F extends VirtualFile> {

  @Nullable F parentOf(@NotNull F file);

  @Nullable F childOf(@NotNull F parent,
                      @NotNull String childName);

  /** Don't resolve symlinks or canonicalize the path -- just walk the path segments as they are. */
  FileNavigator<NewVirtualFile> LEXICAL = new FileNavigator<>() {
    @Override
    public @Nullable NewVirtualFile parentOf(@NotNull NewVirtualFile file) {
      return file.getParent();
    }

    @Override
    public @Nullable NewVirtualFile childOf(@NotNull NewVirtualFile parent,
                                            @NotNull String childName) {
      return parent.findChild(childName);
    }
  };

  /**
   * POSIX path resolution requires resolving _each_ path segment against the file system -- e.g. resolve symlinks _before_
   * the '..' resolution. Here we resolve symlink _only_ before '..' (i.e. in {@link #parentOf(VirtualFile)}), but not in
   * other cases -- which is why it is 'light'.
   */
  FileNavigator<NewVirtualFile> POSIX_LIGHT = new FileNavigator<>() {
    @Override
    public @Nullable NewVirtualFile parentOf(@NotNull NewVirtualFile file) {
      //Here we do 'partial canonicalization' of the path: we resolve symlinks, but _only_ in getParent, i.e. before
      // '..' segments. It makes the resolution unstable: 'a/b/c' and 'a/../a/b/c' could be resolved to different files,
      // depending on is 'a' a symlink or not.
      // And the result differs from regular canonicalization, via VirtualFile.getCanonicalPath() or Path.toRealPath(),
      // there _all_ symlinks are resolved -- and the result also differs from Path.toRealPath(NOFOLLOW_LINKS) where _none_
      // of symlinks are resolved.
      if (file.is(SYMLINK)) {
        NewVirtualFile canonicalFile = file.getCanonicalFile();
        if (LOG.isTraceEnabled()) {
          LOG.trace("[" + file.getPath() + "]: symlink resolved to [" + canonicalFile + "]");
        }
        if (canonicalFile == null) {
          return null;
        }

        file = canonicalFile;
      }
      return file.getParent();
    }

    @Override
    public @Nullable NewVirtualFile childOf(@NotNull NewVirtualFile parent,
                                            @NotNull String childName) {
      return parent.findChild(childName);
    }
  };

  /**
   * Walks through the path given, segment by segment, resolving each segment through the provided navigator
   *
   * @return the {@link NavigateResult#resolved(VirtualFile)}  if successfully resolves the file corresponding to the path given,
   * or {@link NavigateResult#unresolved(VirtualFile)}/{@link NavigateResult#empty()}, if the path can't be resolved fully, or
   * not at all
   */
  @ApiStatus.Internal
  static <F extends VirtualFile> @NotNull NavigateResult<F> navigate(@NotNull NewVirtualFileSystem vfs,
                                                                     @NotNull String path,
                                                                     @NotNull FileNavigator<F> navigator) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = NewVirtualFileSystem.extractRootAndPathSegments(vfs, path);
    if (rootAndPath == null) return NavigateResult.empty();

    F file = (F)rootAndPath.first;
    for (String pathElement : rootAndPath.second) {

      if (pathElement.isEmpty() || ".".equals(pathElement)) {
        continue;
      }

      F fileBefore = file;
      if ("..".equals(pathElement)) {
        file = navigator.parentOf(file);
      }
      else {
        file = navigator.childOf(file, pathElement);
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("[" + fileBefore.getPath() + "]/[" + pathElement + "] resolved to [" + file + "]");
      }

      if (file == null) {
        return NavigateResult.unresolved(fileBefore);
      }
    }

    return NavigateResult.resolved(file);
  }

  @ApiStatus.Internal
  class NavigateResult<F extends VirtualFile> {
    /** Final or partial resolution result, depending on {@link #resolvedFully} */
    private final @Nullable F resolvedFile;
    private final boolean resolvedFully;

    /** Nothing was resolved at all */
    public static <F extends VirtualFile> NavigateResult<F> empty() {
      return new NavigateResult<>(null,  /*success: */ false);
    }

    /** Requested path wasn't fully resolved, here are the best partial result we get */
    public static <F extends VirtualFile> NavigateResult<F> unresolved(@NotNull F lastResolvedFile) {
      return new NavigateResult<>(lastResolvedFile, /*success: */ false);
    }

    /** Requested path was fully resolved */
    public static <F extends VirtualFile> NavigateResult<F> resolved(@NotNull F successfullyResolvedFile) {
      return new NavigateResult<>(successfullyResolvedFile, /*success: */ true);
    }

    private NavigateResult(@Nullable F resolvedFile,
                           boolean resolvedFully) {
      this.resolvedFile = resolvedFile;
      this.resolvedFully = resolvedFully;
    }

    public boolean isResolved() {
      return resolvedFully;
    }

    public @NotNull F resolvedFileOrFail() {
      if (resolvedFile != null) {
        return resolvedFile;
      }
      throw new IllegalStateException("File was not resolved");
    }

    public @Nullable F resolvedFileOr(@Nullable F orElse) {
      if (!isResolved()) {
        return orElse;
      }
      return resolvedFile;
    }

    /**
     * @return Last resolved file during the navigation process: the final result, if {@link #isResolved()} or intermediate
     * result, if not {@link #isResolved()}
     */
    public @Nullable F lastResolvedFile() {
      return resolvedFile;
    }

    @Override
    public String toString() {
      return isResolved() ?
             "NavigateResult[resolved: " + resolvedFile + "]" :
             "NavigateResult[not resolved, last resolved segment: " + resolvedFile + "]";
    }
  }
}
