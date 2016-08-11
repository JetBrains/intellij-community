/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.ide.todo;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashMap;

import java.util.*;

/**
 * @author Vladimir Kondratyev
 */
final class FileTree {
  private static final Logger LOG = Logger.getInstance("#com.intellij.ide.todo.FileTree");

  private final Map<VirtualFile, List<VirtualFile>> myDirectory2Children;
  private final Set<VirtualFile> myFiles;
  private final Map<VirtualFile, List<VirtualFile>> myStrictDirectory2Children;

  FileTree() {
    myDirectory2Children = new HashMap<>();
    myFiles = new HashSet<>();
    myStrictDirectory2Children = new HashMap<>();
  }

  void add(VirtualFile file) {
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
      children = new ArrayList<>(2);
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
      children = new ArrayList<>(2);
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
        children = new ArrayList<>(2);
        children.add(dir);
        myDirectory2Children.put(parent, children);
      }
      dir = parent;
      parent = parent.getParent();
    }
  }

  boolean isDirectoryEmpty(VirtualFile dir) {
    final List<VirtualFile> files = myStrictDirectory2Children.get(dir);
    return files == null || files.isEmpty();
  }

  List<VirtualFile> getFilesUnderDirectory(VirtualFile dir) {
    List<VirtualFile> filesList = new ArrayList<>();
    List<VirtualFile> files = myStrictDirectory2Children.get(dir);
    if (files != null) {
      filesList.addAll(files);
    }
    return filesList;
  }

  void removeFile(VirtualFile file) {
    if (!myFiles.contains(file)) {
      return;
    }

    myFiles.remove(file);
    List<VirtualFile> dirsToBeRemoved = null;
    for (VirtualFile _directory : myDirectory2Children.keySet()) {
      List<VirtualFile> children = myDirectory2Children.get(_directory);
      LOG.assertTrue(children != null);
      if (children.contains(file)) {
        children.remove(file);
        if (children.size() == 0) {
          if (dirsToBeRemoved == null) {
            dirsToBeRemoved = new ArrayList<>(2);
          }
          dirsToBeRemoved.add(_directory); // we have to remove empty _directory
        }
      }
    }
    for (VirtualFile dir : myStrictDirectory2Children.keySet()) {
      List<VirtualFile> children = myStrictDirectory2Children.get(dir);
      LOG.assertTrue(children != null);
      if (children.contains(file)) {
        children.remove(file);
      }
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
   * The method removes specified <code>psiDirectory</code> from the tree. The directory should be empty,
   * otherwise the method shows java.lang.IllegalArgumentException
   */
  private void removeDir(VirtualFile psiDirectory) {
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
      if (children.contains(psiDirectory)) {
        children.remove(psiDirectory);
        if (children.size() == 0) {
          if (dirsToBeRemoved == null) {
            dirsToBeRemoved = new ArrayList<>(2);
          }
          dirsToBeRemoved.add(_directory); // we have remove empty _directory
        }
      }
    }
    //
    if (dirsToBeRemoved != null) {
      for (VirtualFile dirToBeRemoved : dirsToBeRemoved) {
        removeDir(dirToBeRemoved);
      }
    }
  }

  boolean contains(VirtualFile file) {
    return myFiles.contains(file);
  }

  void clear() {
    myStrictDirectory2Children.clear();
    myDirectory2Children.clear();
    myFiles.clear();
  }

  /**
   * @return iterator of all files.
   */
  Iterator<VirtualFile> getFileIterator() {
    return myFiles.iterator();
  }

  /**
   * @return all files (in depth) located under specified <code>psiDirectory</code>.
   *         Please note that returned files can be invalid.
   */
  List<VirtualFile> getFiles(VirtualFile dir) {
    List<VirtualFile> filesList = new ArrayList<>();
    collectFiles(dir, filesList);
    return filesList;
  }

  private void collectFiles(VirtualFile dir, List<VirtualFile> filesList) {
    List<VirtualFile> children = myDirectory2Children.get(dir);
    if (children != null) {
      for (VirtualFile child : children) {
        if (!child.isDirectory()) {
          LOG.assertTrue(!filesList.contains(child));
          filesList.add(child);
        }
        else {
          collectFiles(child, filesList);
        }
      }
    }
  }
}
