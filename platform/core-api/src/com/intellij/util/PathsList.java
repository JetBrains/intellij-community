/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util;

import com.intellij.ide.highlighter.ArchiveFileType;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.HashSet;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

public class PathsList  {
  private final List<String> myPath = new ArrayList<String>();
  private final List<String> myPathTail = new ArrayList<String>();
  private final Set<String> myPathSet = new HashSet<String>();

  private static final Function<String, VirtualFile> PATH_TO_LOCAL_VFILE = new NullableFunction<String, VirtualFile>() {
    @Override
    public VirtualFile fun(String path) {
      return StandardFileSystems.local().findFileByPath(path.replace(File.separatorChar, '/'));
    }
  };

  private static final Function<VirtualFile, String> LOCAL_PATH = new Function<VirtualFile, String>() {
    @Override
    public String fun(VirtualFile file) {
      return PathUtil.getLocalPath(file);
    }
  };

  private static final Function<String, VirtualFile> PATH_TO_DIR = new NullableFunction<String, VirtualFile>() {
    @Override
    public VirtualFile fun(String s) {
      final FileType fileType = FileTypeRegistry.getInstance().getFileTypeByFileName(s);
      final VirtualFile localFile = PATH_TO_LOCAL_VFILE.fun(s);
      if (localFile == null) return null;

      if (ArchiveFileType.INSTANCE.equals(fileType) && !localFile.isDirectory()) {
        return StandardFileSystems.getJarRootForLocalFile(localFile);
      }
      return localFile;
    }
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

  public void add(VirtualFile file) {
    add(LOCAL_PATH.fun(file));
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
      return JBIterable.from(StringUtil.tokenize(path, File.pathSeparator)).filter(new Condition<String>() {
        @Override
        public boolean value(String element) {
          element = element.trim();
          return !element.isEmpty() && !myPathSet.contains(element);
        }
      });
    }
  }

  private void addAllLast(Iterable<String> elements, List<String> toArray) {
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
    List<String> result = new ArrayList<String>();
    result.addAll(myPath);
    result.addAll(myPathTail);
    return result;
  }

  /**
   * @return {@link VirtualFile}s on local file system (returns jars as files).
   */
  public List<VirtualFile> getVirtualFiles() {
    return JBIterable.from(getPathList()).map(PATH_TO_LOCAL_VFILE).filter(Condition.NOT_NULL).toList();
  }

  /**
   * @return The same as {@link #getVirtualFiles()} but returns jars as {@code JarFileSystem} roots.
   */
  public List<VirtualFile> getRootDirs() {
    return JBIterable.from(getPathList()).map(PATH_TO_DIR).filter(Condition.NOT_NULL).toList();
  }

  public void addAll(List<String> allClasspath) {
    for (String path : allClasspath) {
      add(path);
    }
  }

  public void addAllFiles(File[] files) {
    addAllFiles(Arrays.asList(files));
  }

  public void addAllFiles(List<File> files) {
    for (File file : files) {
      add(file);
    }
  }

  public void add(File file) {
    add(FileUtil.toCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  public void addVirtualFiles(Collection<VirtualFile> files) {
    for (VirtualFile file : files) {
      add(file);
    }
  }

  public void addVirtualFiles(VirtualFile[] files) {
    addVirtualFiles(Arrays.asList(files));
  }
}