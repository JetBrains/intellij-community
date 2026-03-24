// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs;

import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.ThrowableComputable;
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
   * POSIX path resolution requires resolving _each_ path segment against the file system -- e.g. resolve all symlinks.
   * This implementation resolves symlink _only_ before '..' (i.e. in {@link #parentOf(VirtualFile)}), but not in other cases
   * -- which is why it is 'light'.
   * It is an optimization, to reduce # of accesses to the actual underlying FS. But it has downsides: the path resolution
   * sometimes gives unexpected result.
   * E.g. 'a/b/c' and 'a/../a/b/c' paths one would expect them to resolve to the same file, since `a/../a` segment should just
   * collapse to `a`. This is true for POSIX resolution, but not for 'POSIX light': under 'POSIX light' result of the path
   * resolution depends on is `a` a symlink or not. If `a` is a symlink (->`/home/user/AAA`), then the symlink will be resolved
   * during 'a/../a/b/c' resolution, but during 'a/b/c' resolution symlink will NOT be resolved -- hence, 'a/../a/b/c' resolves
   * to VirtualFile[`/home/user/AAA/b/c`], while `a/b/c` resolves to just VirtualFile[`a/b/c`].
   * This is important only on the level of VirtualFile operations -- e.g. if one reads VirtualFile[`a/b/c`] content, the read
   * goes through underlying FS, which evaluates the path via true POSIX path resolution, so the `/home/user/AAA/b/c` content
   * is really read. But as long as you work with VirtualFiles, the VirtualFile[`/home/user/AAA/b/c`] != VirtualFile[`a/b/c`],
   * and this difference may sometimes bite.
   */
  FileNavigator<NewVirtualFile> POSIX_LIGHT = new FileNavigator<>() {
    @Override
    public @Nullable NewVirtualFile parentOf(@NotNull NewVirtualFile file) {
      //Here we do 'partial canonicalization' of the path: we resolve symlinks, but _only_ in getParent, i.e. before
      // '..' segments. It makes the resolution "unstable" so to say: 'a/b/c' and 'a/../a/b/c' could be resolved to different
      // files, depending on is 'a' a symlink or not.
      // The result differs from regular canonicalization, via VirtualFile.getCanonicalPath() or Path.toRealPath(),
      // there _all_ symlinks are resolved -- but the result also differs from Path.toRealPath(NOFOLLOW_LINKS) where _none_
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
   * or {@link NavigateResult#unresolved(VirtualFile, String)}/{@link NavigateResult#empty()}, if the path can be resolved only partially,
   * or not at all
   */
  @ApiStatus.Internal
  static <F extends VirtualFile> @NotNull NavigateResult<F> navigate(@NotNull NewVirtualFileSystem vfs,
                                                                     @NotNull String path,
                                                                     @NotNull FileNavigator<F> navigator) {
    Pair<NewVirtualFile, Iterable<String>> rootAndPath = NewVirtualFileSystem.extractRootAndPathSegments(vfs, path);
    if (rootAndPath == null) {
      if (LOG.isTraceEnabled()) {
        LOG.trace("extractRootAndPathSegments(" + vfs + ", " + path + ") = null");
      }
      return NavigateResult.empty();
    }

    //noinspection unchecked
    F rootToStartFrom = (F)rootAndPath.first;
    Iterable<String> pathElements = rootAndPath.second;

    //IJPL-218716: there is no solution about how FileDeletedException could happen (seems like a race, or
    // corruption, i.e. a bug in code, but details are not clear) -- lets just retry the resolution 3 times:
    return retryUpToN(
      () -> followPath(navigator, rootToStartFrom, pathElements),
      /*maxAttempts: */ 3,
      FileDeletedException.class
    );
  }

  private static <F extends VirtualFile> @NotNull NavigateResult<F> followPath(@NotNull FileNavigator<F> navigator,
                                                                               @NotNull F startingWithFile,
                                                                               @NotNull Iterable<String> pathElementsToFollow) {
    F currentFile = startingWithFile;
    for (String pathElement : pathElementsToFollow) {

      if (pathElement.isEmpty() || ".".equals(pathElement)) {
        continue;
      }

      F fileBefore = currentFile;
      boolean navigationToChild = false;
      if ("..".equals(pathElement)) {
        currentFile = navigator.parentOf(currentFile);
      }
      else {
        navigationToChild = true;
        currentFile = navigator.childOf(currentFile, pathElement);
      }

      if (LOG.isTraceEnabled()) {
        LOG.trace("[" + fileBefore.getPath() + "]/[" + pathElement + "] resolved to [" + currentFile + "]");
      }

      if (currentFile == null) {
        return NavigateResult.unresolved(fileBefore, navigationToChild ? pathElement : null);
      }
    }

    return NavigateResult.resolved(currentFile);
  }

  /**
   * If computable throws an exception of class exceptionTypeToRetry => retry computable up to maxAttempts
   * times.
   * Other exceptions rethrown immediately, without retries.
   * If maxAttempts were exhausted -- the exceptions thrown by computable are rethrown in the following way:
   * the exception thrown on 1st attempt is the exception actually rethrown, while exceptions thrown on
   * 2-3-4..th attempts are attached to it as .suppressed.
   * MAYBE RC: move to some generic Utils class? -- seems to be generally-useful method
   */
  private static <V, E extends Exception> V retryUpToN(@NotNull ThrowableComputable<V, E> computable,
                                                       int maxAttempts,
                                                       @NotNull Class<E> exceptionTypeToRetry) throws E {
    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts(=" + maxAttempts + ") must be >0");
    }

    E mainEx = null;
    for (int attempt = 0; attempt < maxAttempts; attempt++) {
      try {
        V result = computable.compute();
        if (mainEx != null) {//reports to FUS -> will be able to collect the stats in Diogen:
          LOG.error("Computation succeed on attempt#" + attempt + ", but exception(s) were thrown during previous attempt(s)", mainEx);
        }
        return result;
      }
      catch (Throwable t) {
        if (!exceptionTypeToRetry.isInstance(t)) {
          if (mainEx != null) {
            t.addSuppressed(mainEx); //provide as much info as possible
          }
          throw t;
        }

        if (mainEx == null) {
          //noinspection unchecked
          mainEx = (E)t;
        }
        else {
          mainEx.addSuppressed(t);
        }
      }
    }
    throw mainEx;
  }

  @ApiStatus.Internal
  class NavigateResult<F extends VirtualFile> {
    /** Final or partial resolution result, depending on {@link #resolvedFully} */
    private final @Nullable F resolvedFile;
    private final @Nullable String unresolvedChildName;
    private final boolean resolvedFully;

    /** Nothing was resolved at all */
    public static <F extends VirtualFile> NavigateResult<F> empty() {
      return new NavigateResult<>(null, null,  /*success: */ false);
    }

    /** Requested path wasn't fully resolved, here are the best partial result we get */
    public static <F extends VirtualFile> NavigateResult<F> unresolved(@NotNull F lastResolvedFile, @Nullable String unresolvedChildName) {
      return new NavigateResult<>(lastResolvedFile, unresolvedChildName, /*success: */ false);
    }

    /** Requested path was fully resolved */
    public static <F extends VirtualFile> NavigateResult<F> resolved(@NotNull F successfullyResolvedFile) {
      return new NavigateResult<>(successfullyResolvedFile, null, /*success: */ true);
    }

    private NavigateResult(@Nullable F resolvedFile,
                           @Nullable String unresolvedChildName,
                           boolean resolvedFully) {
      this.resolvedFile = resolvedFile;
      this.unresolvedChildName = unresolvedChildName;
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

    public @Nullable String getUnresolvedChildName() {
      return unresolvedChildName;
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
