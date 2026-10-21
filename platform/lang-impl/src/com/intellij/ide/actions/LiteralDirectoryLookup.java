// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.IndexNotReadyException;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;

/**
 * Resolves a Goto File directory pattern that is a literal path, which is what a pasted path looks like.
 * <p>
 * The lookup stays inside the VFS cache and is case-sensitive, so it answers nothing for a half-typed or a
 * mis-cased path. It also drops a directory that only the fuzzy matcher of {@link DirectoryPathMatcher#appendChar}
 * keeps, so it is no replacement for that walk. See {@link DirectoryPathMatcher#literalRoot}.
 * <p>
 * A directory comes with its full name, which is the name that the popup shows.
 */
final class LiteralDirectoryLookup {
  /** Resolve a directory pattern that is a literal path through the VFS cache, instead of walking the tree. */
  private static final String REGISTRY_KEY = "search.everywhere.goto.file.literal.directory.match";

  /** How many same-named directories the lookup accepts before it gives up. */
  private static final int CANDIDATE_LIMIT = 10_000;
  /** The lookup runs only for a path this deep, because a shorter one carries too little information. */
  private static final int MIN_SEPARATORS = 2;

  private final @NotNull GotoFileModel myModel;
  private final @NotNull Predicate<VirtualFile> myProjectFileFilter;
  /** The pattern without the trailing slash. */
  private final @NotNull String myPath;

  private LiteralDirectoryLookup(@NotNull GotoFileModel model,
                                 @NotNull Predicate<VirtualFile> projectFileFilter,
                                 @NotNull String path) {
    myModel = model;
    myProjectFileFilter = projectFileFilter;
    myPath = path;
  }

  /**
   * Every directory that the directory pattern can mean.
   *
   * @return an empty list when the pattern is not a literal path, when the registry key is off,
   * or when no directory matches the path
   */
  static @NotNull List<NamedDirectory> findDirectories(@NotNull GotoFileModel model,
                                                       @NotNull Predicate<VirtualFile> projectFileFilter,
                                                       @NotNull String dirPattern) {
    if (!Registry.is(REGISTRY_KEY, true)) return Collections.emptyList();

    String path = StringUtil.trimEnd(dirPattern, "/");
    if (!isLiteralPath(path)) return Collections.emptyList();

    return new LiteralDirectoryLookup(model, projectFileFilter, path).collectDirectories();
  }

  /** @return whether the path needs no fuzzy match, so that a lookup can resolve it */
  private static boolean isLiteralPath(@NotNull String path) {
    if (path.isEmpty() || path.indexOf('*') >= 0 || path.indexOf(' ') >= 0 || path.contains("//")) return false;
    return StringUtil.countChars(path, '/') >= MIN_SEPARATORS;
  }

  /**
   * Unions the two sources of the lookup and removes a duplicate directory.
   * Both sources run, because one path can name a directory under a base project directory and also name
   * a directory of the same relative path in another module. A project with many modules holds the same
   * path, such as {@code src/main/java}, in each of them.
   */
  private @NotNull List<NamedDirectory> collectDirectories() {
    List<NamedDirectory> result = new ArrayList<>();
    Set<VirtualFile> found = new HashSet<>();
    // one base root can answer twice, so the duplicate removal covers both sources
    for (NamedDirectory dir : ContainerUtil.concat(findUnderBaseDirectories(), findByPathSuffix())) {
      if (found.add(dir.file())) {
        result.add(dir);
      }
    }
    return result;
  }

  /** Resolves the path under each base project directory, one segment at a time. */
  private @NotNull List<NamedDirectory> findUnderBaseDirectories() {
    List<NamedDirectory> result = new ArrayList<>();
    for (NamedDirectory root : getBaseRoots()) {
      // both readings of the path can name a real directory, so both go into the result
      addCachedDirectory(result, root, myPath);
      // the full name of a file in the popup starts with the name of its root, so a copied path can repeat it
      String rootPrefix = root.file().getName() + '/';
      if (myPath.startsWith(rootPrefix)) {
        addCachedDirectory(result, root, myPath.substring(rootPrefix.length()));
      }
    }
    return result;
  }

  /**
   * The roots that a literal relative path can start from.
   * Only the base directories, because {@link DirectoryPathMatcher#getProjectRoots} also resolves every library
   * and SDK root through the workspace model, which costs more than the walk this class avoids.
   * A path inside a library is left to {@link #findByPathSuffix}.
   */
  private @NotNull List<NamedDirectory> getBaseRoots() {
    Set<VirtualFile> roots = new HashSet<>();
    for (VirtualFile base : BaseProjectDirectories.getBaseDirectories(myModel.getProject())) {
      VirtualFile top = myModel.getTopLevelRoot(base);
      roots.add(top != null ? top : base);
    }
    return ContainerUtil.map(roots, root -> NamedDirectory.of(myModel, root));
  }

  private void addCachedDirectory(@NotNull List<NamedDirectory> result,
                                  @NotNull NamedDirectory root,
                                  @NotNull String relativePath) {
    VirtualFile dir = findCachedDirectory(root.file(), relativePath);
    if (dir != null && myProjectFileFilter.test(dir)) {
      result.add(root.child(dir));
    }
  }

  private static @Nullable VirtualFile findCachedDirectory(@NotNull VirtualFile root, @NotNull String relativePath) {
    VirtualFile current = root;
    for (String name : StringUtil.split(relativePath, "/")) {
      if (!current.isDirectory()) return null;
      current = current instanceof NewVirtualFile newVirtualFile ? newVirtualFile.findChildIfCached(name) : current.findChild(name);
      if (current == null) return null;
    }
    return current.isDirectory() ? current : null;
  }

  /**
   * Finds a directory whose path ends with the path of the pattern.
   * This covers a path that is relative to a nested content root.
   * The name index answers nothing in dumb mode, and {@link #findUnderBaseDirectories} still answers there.
   */
  private @NotNull List<NamedDirectory> findByPathSuffix() {
    String name = myPath.substring(myPath.lastIndexOf('/') + 1);
    if (name.isEmpty()) return Collections.emptyList();

    String suffix = '/' + myPath;
    List<NamedDirectory> result = new ArrayList<>();
    AtomicInteger counter = new AtomicInteger();
    GlobalSearchScope scope = GlobalSearchScope.allScope(myModel.getProject());
    try {
      boolean scannedAll = FilenameIndex.processFilesByName(name, true, scope, file -> {
        if (counter.incrementAndGet() > CANDIDATE_LIMIT) return false;
        if (file.isDirectory() && file.getPath().endsWith(suffix) && myProjectFileFilter.test(file)) {
          result.add(NamedDirectory.of(myModel, file));
        }
        return true;
      });
      // too many same-named directories: an arbitrary prefix of them helps nobody, so the lookup answers less
      if (!scannedAll) return Collections.emptyList();
    }
    catch (IndexNotReadyException e) {
      return Collections.emptyList();
    }
    return result;
  }
}
