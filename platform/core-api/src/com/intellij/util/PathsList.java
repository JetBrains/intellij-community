// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

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

  public boolean isEmpty() {
    return myPathSet.isEmpty();
  }

  public void add(String path) {
    addAllLast(chooseFirstTimeItems(path), myPath);
  }

  public void remove(@NotNull String path) {
    myPath.remove(path);
    myPathTail.remove(path);
    myPathSet.remove(path);
  }

  public void clear() {
    myPath.clear();
    myPathTail.clear();
    myPathSet.clear();
  }

  public void add(VirtualFile file) {
    String path = LOCAL_PATH.fun(file);
    String trimmed = path != null ? path.trim() : "";
    if (!trimmed.isEmpty() && myPathSet.add(trimmed)) {
      myPath.add(trimmed);
    }
  }

  public void addFirst(String path) {
    int index = 0;
    for (String element : chooseFirstTimeItems(path)) {
      myPath.add(index, element);
      myPathSet.add(element);
      index++;
    }
  }

  public void addTail(String path) {
    addAllLast(chooseFirstTimeItems(path), myPathTail);
  }

  private Iterable<String> chooseFirstTimeItems(String path) {
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

  @NotNull
  public String getPathsString() {
    return StringUtil.join(getPathList(), File.pathSeparator);
  }

  @NotNull
  public List<String> getPathList() {
    List<String> result = new ArrayList<>();
    result.addAll(myPath);
    result.addAll(myPathTail);
    return result;
  }

  /**
   * @return {@link VirtualFile}s on local file system (returns jars as files).
   */
  public List<VirtualFile> getVirtualFiles() {
    return JBIterable.from(getPathList()).filterMap(PATH_TO_LOCAL_VFILE).toList();
  }

  /**
   * @return The same as {@link #getVirtualFiles()} but returns jars as {@code JarFileSystem} roots.
   */
  public List<VirtualFile> getRootDirs() {
    return JBIterable.from(getPathList()).filterMap(PATH_TO_DIR).toList();
  }

  public void addAll(List<String> allClasspath) {
    for (String path : allClasspath) {
      add(path);
    }
  }

  public void addAllFiles(File[] files) {
    addAllFiles(Arrays.asList(files));
  }

  public void addAllFiles(List<? extends File> files) {
    for (File file : files) {
      add(file);
    }
  }

  public void add(File file) {
    add(FileUtil.toCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  public void addFirst(File file) {
    addFirst(FileUtil.toCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  public void addVirtualFiles(Collection<? extends VirtualFile> files) {
    for (VirtualFile file : files) {
      add(file);
    }
  }

  public void addVirtualFiles(VirtualFile[] files) {
    addVirtualFiles(Arrays.asList(files));
  }
}