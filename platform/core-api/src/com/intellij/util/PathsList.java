// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.util.*;

/**
 * A list of unique paths consisting of three parts: First, Middle, Tail.
 */
public final class PathsList  {
  private final List<String> myPath = new ArrayList<>();
  private final List<String> myPathTail = new ArrayList<>();
  private final Set<String> myPathSet = new HashSet<>();

  private static final Function<String, VirtualFile> PATH_TO_LOCAL_VFILE =
    path -> StandardFileSystems.local().findFileByPath(path.replace(File.separatorChar, '/'));

  private static final Function<VirtualFile, String> LOCAL_PATH = file -> PathUtil.getLocalPath(file);

  private static final Function<String, VirtualFile> PATH_TO_DIR = s -> {
    VirtualFile file = PATH_TO_LOCAL_VFILE.fun(s);
    if (file == null) return null;
    if (!file.isDirectory() && FileTypeRegistry.getInstance().getFileTypeByFileName(file.getNameSequence()) == ArchiveFileType.INSTANCE) {
      return StandardFileSystems.jar().findFileByPath(file.getPath() + URLUtil.JAR_SEPARATOR);
    }
    return file;
  };

  /**
   * @return whether there are paths inside this path list.
   */
  public boolean isEmpty() {
    return myPathSet.isEmpty();
  }

  /**
   * Adds the passed path to the middle part of the path list.
   */
  public void add(@Nullable String path) {
    addAllLast(chooseFirstTimeItems(path), myPath);
  }

  /**
   * Removes the path to this virtual file from the path list.
   */
  public void remove(@NotNull VirtualFile file) {
    String path = LOCAL_PATH.fun(file);
    remove(path);
  }

  /**
   * Removes the passed path from this path list.
   */
  public void remove(@NotNull String path) {
    myPath.remove(path);
    myPathTail.remove(path);
    myPathSet.remove(path);
  }

  /**
   * Clears all parts of this path list.
   */
  public void clear() {
    myPath.clear();
    myPathTail.clear();
    myPathSet.clear();
  }

  /**
   * Adds the path to the passed virtual file to the middle part of the path list.
   */
  public void add(VirtualFile file) {
    String path = LOCAL_PATH.fun(file);
    String trimmed = path != null ? path.trim() : "";
    if (!trimmed.isEmpty() && myPathSet.add(trimmed)) {
      myPath.add(trimmed);
    }
  }

  /**
   * Adds the passed path to the first part of the path list.
   * It is possible to pass multiple paths at once by separating them with {@link File#pathSeparator}.
   */
  public void addFirst(String path) {
    int index = 0;
    for (String element : chooseFirstTimeItems(path)) {
      myPath.add(index, element);
      myPathSet.add(element);
      index++;
    }
  }

  /**
   * Adds the passed path to the tail part of the path list.
   * It is possible to pass multiple paths at once by separating them with {@link File#pathSeparator}.
   */
  public void addTail(String path) {
    addAllLast(chooseFirstTimeItems(path), myPathTail);
  }

  private @NotNull Iterable<String> chooseFirstTimeItems(@Nullable String path) {
    if (path == null) {
      return Collections.emptyList();
    }
    else {
      return JBIterable.from(StringUtil.tokenize(path, File.pathSeparator)).filter(element -> {
        element = element.trim();
        return !element.isEmpty() && !myPathSet.contains(element);
      });
    }
  }

  private void addAllLast(Iterable<String> elements, List<? super String> toArray) {
    for (String element : elements) {
      toArray.add(element);
      myPathSet.add(element);
    }
  }

  /**
   * @return ll paths ordered by first, middle, tail concatenated by {@link File#pathSeparator} as a single String.
   */
  public @NotNull String getPathsString() {
    return StringUtil.join(getPathList(), File.pathSeparator);
  }

  /**
   * @return All paths ordered by first, middle, tail as a list of Strings.
   */
  public @NotNull @Unmodifiable List<String> getPathList() {
    return ContainerUtil.concat(myPath, myPathTail);
  }

  /**
   * @return {@link VirtualFile}s on local file system (returns jars as files).
   */
  public @Unmodifiable List<VirtualFile> getVirtualFiles() {
    return JBIterable.from(getPathList()).filterMap(PATH_TO_LOCAL_VFILE).toList();
  }

  /**
   * @return The same as {@link #getVirtualFiles()} but returns jars as {@code JarFileSystem} roots.
   */
  public @Unmodifiable List<VirtualFile> getRootDirs() {
    return JBIterable.from(getPathList()).filterMap(PATH_TO_DIR).toList();
  }

  /**
   * Adds the path to the passed paths to the middle part of the path list.
   */
  public void addAll(List<String> allClasspath) {
    for (String path : allClasspath) {
      add(path);
    }
  }

  /**
   * Adds the path to the passed files to the middle part of the path list.
   */
  public void addAllFiles(File[] files) {
    addAllFiles(Arrays.asList(files));
  }

  /**
   * Adds the path to the passed files to the middle part of the path list.
   */
  public void addAllFiles(List<? extends File> files) {
    for (File file : files) {
      add(file);
    }
  }

  /**
   * Adds the path to the passed file to the middle part of the path list.
   */
  public void add(File file) {
    add(FileUtil.toCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  /**
   * Adds the path to the passed file to the first part of the path list.
   */
  public void addFirst(File file) {
    addFirst(FileUtil.toCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  /**
   * Adds the path to the passed files to the middle part of the path list.
   */
  public void addVirtualFiles(Collection<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      add(file);
    }
  }

  /**
   * Adds the path to the passed files to the middle part of the path list.
   */
  public void addVirtualFiles(VirtualFile[] files) {
    addVirtualFiles(Arrays.asList(files));
  }
}