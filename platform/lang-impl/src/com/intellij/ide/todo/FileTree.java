// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.todo;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.registry.RegistryManager;
import com.intellij.openapi.util.registry.RegistryValue;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

final class FileTree {

  private static final Logger LOG = Logger.getInstance(FileTree.class);
  private static final @NotNull RegistryValue ASSERT_THREADS = RegistryManager.getInstance().get("ide.tree.ui.assert.threads");

  private final Map<VirtualFile, List<VirtualFile>> myDirectory2Children;
  private final Set<VirtualFile> myFiles;
  private final Map<VirtualFile, List<VirtualFile>> myStrictDirectory2Children;

  FileTree() {
    myDirectory2Children = new ConcurrentHashMap<>();
    myFiles = ContainerUtil.newConcurrentSet();
    myStrictDirectory2Children = new ConcurrentHashMap<>();
  }

  int size() {
    return myFiles.size();
  }

  void add(@NotNull VirtualFile file) {
    assertThreadIfNeeded();

    if (myFiles.contains(file)) {
      return;
    }

    VirtualFile dir = file.getParent();
    if (dir == null) {
      LOG.error(file);
      return;
    }

    myFiles.add(file);

    List<VirtualFile> children = myStrictDirectory2Children.get(dir);
    if (children != null) {
      LOG.assertTrue(!children.contains(file));
      children.add(file);
    }
    else {
      children = ContainerUtil.createConcurrentList();
      children.add(file);
      myStrictDirectory2Children.put(dir, children);
    }

    children = myDirectory2Children.get(dir);
    if (children != null) {
      LOG.assertTrue(!children.contains(file));
      children.add(file);
      return;
    }
    else {
      children = ContainerUtil.createConcurrentList();
      children.add(file);
      myDirectory2Children.put(dir, children);
    }

    VirtualFile parent = dir.getParent();
    while (parent != null) {
      children = myDirectory2Children.get(parent);
      if (children != null) {
        if ((!children.contains(dir))) {
          children.add(dir);
        }
        return;
      }
      else {
        children = ContainerUtil.createConcurrentList();
        children.add(dir);
        myDirectory2Children.put(parent, children);
      }
      dir = parent;
      parent = parent.getParent();
    }
  }


  boolean isDirectoryEmpty(@NotNull VirtualFile dir) {
    assertThreadIfNeeded();

    return myStrictDirectory2Children.getOrDefault(dir, List.of()).isEmpty();
  }

  @NotNull List<VirtualFile> getFilesUnderDirectory(@NotNull VirtualFile dir) {
    assertThreadIfNeeded();

    List<VirtualFile> files = myStrictDirectory2Children.get(dir);
    return files != null ? List.copyOf(files) : List.of();
  }

  void removeFile(@NotNull VirtualFile file) {
    assertThreadIfNeeded();

    if (!myFiles.contains(file)) {
      return;
    }

    myFiles.remove(file);
    List<VirtualFile> dirsToBeRemoved = null;
    for (VirtualFile _directory : myDirectory2Children.keySet()) {
      List<VirtualFile> children = myDirectory2Children.get(_directory);
      LOG.assertTrue(children != null);
      dirsToBeRemoved = collectDirsToRemove(file, children, dirsToBeRemoved, _directory);
    }
    for (VirtualFile dir : myStrictDirectory2Children.keySet()) {
      List<VirtualFile> children = myStrictDirectory2Children.get(dir);
      LOG.assertTrue(children != null);
      children.remove(file);
    }
    // We have remove also all removed (empty) directories
    if (dirsToBeRemoved != null) {
      LOG.assertTrue(dirsToBeRemoved.size() > 0);
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  /**
   * The method removes specified {@code psiDirectory} from the tree. The directory should be empty,
   * otherwise the method shows java.lang.IllegalArgumentException
   */
  private void removeDir(@NotNull VirtualFile psiDirectory) {
    if (!myDirectory2Children.containsKey(psiDirectory)) {
      throw new IllegalArgumentException("directory is not in the tree: " + psiDirectory);
    }
    List<VirtualFile> children = myDirectory2Children.remove(psiDirectory);
    if (children == null) {
      throw new IllegalArgumentException("directory has no children list: " + psiDirectory);
    }
    if (children.size() > 0) {
      throw new IllegalArgumentException("directory isn't empty: " + psiDirectory);
    }
    //
    myStrictDirectory2Children.remove(psiDirectory);
    List<VirtualFile> dirsToBeRemoved = null;
    for (VirtualFile _directory : myDirectory2Children.keySet()) {
      children = myDirectory2Children.get(_directory);
      LOG.assertTrue(children != null);
      dirsToBeRemoved = collectDirsToRemove(psiDirectory, children, dirsToBeRemoved, _directory);
    }
    //
    if (dirsToBeRemoved != null) {
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  private static List<VirtualFile> collectDirsToRemove(@NotNull VirtualFile psiDirectory,
                                                       @NotNull List<VirtualFile> children,
                                                       List<VirtualFile> dirsToBeRemoved,
                                                       @NotNull VirtualFile _directory) {
    if (children.remove(psiDirectory)) {
      if (children.size() == 0) {
        if (dirsToBeRemoved == null) {
          dirsToBeRemoved = new ArrayList<>(2);
        }
        dirsToBeRemoved.add(_directory); // we have remove empty _directory
      }
    }
    return dirsToBeRemoved;
  }

  boolean contains(@NotNull VirtualFile file) {
    return myFiles.contains(file);
  }

  void clear() {
    assertThreadIfNeeded();

    myStrictDirectory2Children.clear();
    myDirectory2Children.clear();
    myFiles.clear();
  }

  /**
   * @return iterator of all files.
   */
  @NotNull Iterator<VirtualFile> getFileIterator() {
    assertThreadIfNeeded();

    return myFiles.iterator();
  }

  /**
   * @return all files (in depth) located under specified {@code psiDirectory}.
   * Please note that returned files can be invalid.
   */
  @NotNull List<VirtualFile> getFiles(@NotNull VirtualFile dir) {
    assertThreadIfNeeded();
    ApplicationManager.getApplication().assertReadAccessAllowed();

    List<VirtualFile> filesList = new ArrayList<>();
    collectFiles(dir, filesList);
    return Collections.unmodifiableList(filesList);
  }

  private void collectFiles(@NotNull VirtualFile dir,
                            @NotNull List<? super VirtualFile> filesList) {
    for (VirtualFile child : myDirectory2Children.getOrDefault(dir, Collections.emptyList())) {
      ProgressManager.checkCanceled();

      if (!child.isDirectory()) {
        if (LOG.isDebugEnabled()) {
          LOG.assertTrue(!filesList.contains(child));
        }
        filesList.add(child);
      }
      else {
        collectFiles(child, filesList);
      }
    }
  }

  static void assertThreadIfNeeded() {
    if (ASSERT_THREADS.asBoolean()) {
      ApplicationManager.getApplication().assertIsNonDispatchThread();
    }
  }
}