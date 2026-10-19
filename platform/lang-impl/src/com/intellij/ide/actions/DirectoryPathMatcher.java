// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions;

import com.intellij.ide.util.gotoByName.GotoFileModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.BaseProjectDirectories;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.LibraryOrSdkOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileVisitor;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.psi.codeStyle.MinusculeMatcher;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.GlobalSearchScopesCore;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.indexing.IdFilter;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Narrows the directories that a Goto File pattern can point to.
 * <p>
 * The matcher is immutable. {@link #root} builds the chain for the directory part of a pattern, and
 * {@link #appendChar} extends it for the file-name part.
 * <p>
 * {@link #literalRoot} builds another chain, from {@link LiteralDirectoryLookup}. It is fast, and it holds a
 * subset of the walk, because it drops a directory that only the fuzzy matcher keeps. It is therefore no replacement
 * for the walk. {@link GotoFileItemProvider} runs it as a first chain and the walk after it, so no result is lost.
 */
final class DirectoryPathMatcher {
  private final @NotNull GotoFileModel myModel;
  private final @Nullable List<NamedDirectory> myFiles;
  private final @NotNull Predicate<VirtualFile> myProjectFileFilter;

  final @NotNull String dirPattern;

  private DirectoryPathMatcher(@NotNull GotoFileModel model,
                               @Nullable List<NamedDirectory> files,
                               @NotNull String pattern,
                               @NotNull Predicate<VirtualFile> projectFileFilter) {
    myModel = model;
    myFiles = files;
    dirPattern = pattern;
    myProjectFileFilter = projectFileFilter;
  }

  private @NotNull DirectoryPathMatcher derive(@Nullable List<NamedDirectory> files, @NotNull String pattern) {
    return new DirectoryPathMatcher(myModel, files, pattern, myProjectFileFilter);
  }

  private static @NotNull Predicate<VirtualFile> createProjectFileFilter(@NotNull Project project) {
    FileBasedIndex fileBasedIndex = FileBasedIndex.getInstanceIfDefined();
    IdFilter projectIndexableFilesFilter = fileBasedIndex == null ? null : fileBasedIndex.projectIndexableFiles(project);
    GlobalSearchScope allScope = GlobalSearchScope.allScope(project);
    if (projectIndexableFilesFilter == null) {
      return vFile -> allScope.contains(vFile);
    }
    return vFile -> {
      return vFile instanceof VirtualFileWithId fileWithId
             ? projectIndexableFilesFilter.containsFileId(fileWithId.getId()) || allScope.contains(vFile)
             : allScope.contains(vFile);
    };
  }

  private static @NotNull DirectoryPathMatcher createRoot(@NotNull GotoFileModel model) {
    return new DirectoryPathMatcher(model, null, "", createProjectFileFilter(model.getProject()));
  }

  static @Nullable DirectoryPathMatcher root(@NotNull GotoFileModel model, @NotNull String pattern) {
    DirectoryPathMatcher matcher = createRoot(model);
    for (int i = 0; i < pattern.length(); i++) {
      matcher = matcher.appendChar(pattern.charAt(i));
      if (matcher == null) return null;
    }
    return matcher;
  }

  @Nullable
  DirectoryPathMatcher appendChar(char c) {
    String nextPattern = dirPattern + c;
    if (c == '*' || c == '/' || c == ' ') return appendNonWalkingChar(c);

    List<NamedDirectory> nextRoots = new ArrayList<>();
    MinusculeMatcher matcher = GotoFileItemProvider.getQualifiedNameMatcher(nextPattern);
    for (NamedDirectory root : getMatchingRoots()) {
      if (containsChar(root.fullName(), c) && matcher.matches(root.fullName())) {
        nextRoots.add(root);
      }
      else {
        processProjectFilesUnder(root.file(), sub -> {
          if (!sub.isDirectory()) return false;
          if (!containsChar(sub.getNameSequence(), c)) return true; //go deeper

          NamedDirectory child = root.child(sub);
          if (matcher.matches(child.fullName())) {
            nextRoots.add(child);
            return false;
          }
          return true;
        });
      }
    }

    return nextRoots.isEmpty() ? null : derive(nextRoots, nextPattern);
  }

  /** Extends the pattern by a character that drives no walk, so the directories stay as they are. */
  private @NotNull DirectoryPathMatcher appendNonWalkingChar(char c) {
    return derive(myFiles, dirPattern + c);
  }

  /** return null if not cheap */
  @Nullable
  Set<String> findFileNamesMatchingIfCheap(char nextLetter, MinusculeMatcher matcher) {
    Set<String> names = new HashSet<>();
    AtomicInteger counter = new AtomicInteger();
    BooleanSupplier tooMany = () -> counter.get() > 1000;
    for (NamedDirectory root : getMatchingRoots()) {
      // Commented this out because the pattern Utils/TT matched the original "Utils" directory name and nothing more here.
      // ("Utils" contain "t", "Utils" is matched by a ["T" "T"] matcher)
      //if (containsChar(root.fullName(), nextLetter) && matcher.matches(root.fullName())) {
      //  names.add(root.file().getName());
      //} else {
      processProjectFilesUnder(root.file(), sub -> {
        counter.incrementAndGet();
        if (tooMany.getAsBoolean()) return false;

        String name = sub.getName();
        if (containsChar(name, nextLetter) && matcher.matches(name)) {
          names.add(name);
        }
        return true;
      });
      //}
    }
    return tooMany.getAsBoolean() ? null : names;
  }

  private @NotNull List<NamedDirectory> getMatchingRoots() {
    return myFiles != null ? myFiles : getProjectRoots(myModel);
  }

  @NotNull
  GlobalSearchScope narrowDown(@NotNull GlobalSearchScope fileSearchScope) {
    if (myFiles == null) return fileSearchScope;

    VirtualFile[] array = ContainerUtil.map2Array(myFiles, VirtualFile.class, NamedDirectory::file);
    return GlobalSearchScopesCore.directoriesScope(myModel.getProject(), true, array).intersectWith(fileSearchScope);
  }

  /**
   * Builds the chain for a directory part that is a literal path, which is what a pasted path looks like.
   * The chain holds a subset of the walk, so the caller must run {@link #root} after it.
   * See {@link LiteralDirectoryLookup}.
   *
   * @return {@code null} when the lookup finds no directory for the pattern
   */
  static @Nullable DirectoryPathMatcher literalRoot(@NotNull GotoFileModel model, @NotNull String pattern) {
    DirectoryPathMatcher matcher = createRoot(model);
    List<NamedDirectory> roots = LiteralDirectoryLookup.findDirectories(model, matcher.myProjectFileFilter, pattern);
    return roots.isEmpty() ? null : matcher.derive(roots, pattern);
  }

  private void processProjectFilesUnder(VirtualFile root, Processor<? super VirtualFile> consumer) {
    VfsUtilCore.visitChildrenRecursively(root, new VirtualFileVisitor<Void>() {

      @Override
      public boolean visitFile(@NotNull VirtualFile file) {
        return myProjectFileFilter.test(file) && consumer.process(file);
      }

      @Override
      public @Nullable Iterable<VirtualFile> getChildrenIterable(@NotNull VirtualFile file) {
        return file instanceof NewVirtualFile ? ((NewVirtualFile)file).getCachedChildren() : null;
      }
    });
  }

  private static boolean containsChar(CharSequence name, char c) {
    return StringUtil.indexOf(name, c, 0, name.length(), false) >= 0;
  }

  @ApiStatus.Internal
  static @NotNull List<NamedDirectory> getProjectRoots(GotoFileModel model) {
    Set<VirtualFile> roots = new HashSet<>(BaseProjectDirectories.getBaseDirectories(model.getProject()));

    for (Module module : ModuleManager.getInstance(model.getProject()).getModules()) {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry instanceof LibraryOrSdkOrderEntry) {
          Collections.addAll(roots, ((LibraryOrSdkOrderEntry)entry).getRootFiles(OrderRootType.CLASSES));
          Collections.addAll(roots, ((LibraryOrSdkOrderEntry)entry).getRootFiles(OrderRootType.SOURCES));
        }
      }
    }
    for (AdditionalLibraryRootsProvider provider : AdditionalLibraryRootsProvider.EP_NAME.getExtensionList()) {
      for (SyntheticLibrary descriptor : provider.getAdditionalProjectLibraries(model.getProject())) {
        roots.addAll(descriptor.getSourceRoots());
        roots.addAll(descriptor.getBinaryRoots());
      }
    }
    return roots.stream()
      .map(root -> {
        VirtualFile top = model.getTopLevelRoot(root);
        return top != null ? top : root;
      })
      .distinct()
      .map(root -> NamedDirectory.of(model, root))
      .collect(Collectors.toList());
  }
}
