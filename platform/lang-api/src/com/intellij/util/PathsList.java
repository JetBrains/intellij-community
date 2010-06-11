/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.FilteringIterator;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;

import static com.intellij.util.containers.ContainerUtil.*;

public class PathsList  {
  private final List<String>  myPath = new ArrayList<String>();
  private final List<String> myPathTail = new ArrayList<String>();
  private final Set<String> myPathSet = new HashSet<String>();

  private static final Function<String, VirtualFile> PATH_TO_LOCAL_VFILE = new Function<String, VirtualFile>() {
    public VirtualFile fun(String path) {
      return LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
    }
  };

  private static final Function<VirtualFile, String> LOCAL_PATH =
    new Function<VirtualFile, String>() {
      public String fun(VirtualFile file) {
        return PathUtil.getLocalPath(file);
      }
    };

  private static final Function<String,VirtualFile> PATH_TO_DIR =
    new Function<String, VirtualFile>() {
      public VirtualFile fun(String s) {
        final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(s);
        final VirtualFile localFile = PATH_TO_LOCAL_VFILE.fun(s);
        if (localFile == null) return null;
        
        if (FileTypes.ARCHIVE.equals(fileType) && !localFile.isDirectory()) {
          return JarFileSystem.getInstance().getJarRootForLocalFile(localFile);
        }
        return localFile;
      }
    };

  public void add(@NonNls String path) {
    addAllLast(chooseFirstTimeItems(path), myPath);
  }

  public void remove(String path) {
    myPath.remove(path);
    myPathSet.remove(path);
  }

  public void add(VirtualFile file) {
    add(LOCAL_PATH.fun(file));
  }

  public void addFirst(@NonNls String path) {
    final Iterator<String> elements = chooseFirstTimeItems(path);
    int index = 0;
    while (elements.hasNext()) {
      final String element = elements.next();
      myPath.add(index, element);
      myPathSet.add(element);
      index++;
    }
  }

  public void addTail(String path) {
    addAllLast(chooseFirstTimeItems(path), myPathTail);
  }

  private Iterator<String> chooseFirstTimeItems(String path) {
    if (path == null) {
      return emptyIterator();
    }
    final StringTokenizer tokenizer = new StringTokenizer(path, File.pathSeparator);
    // in JDK 1.5 StringTokenizer implements Enumeration<Object> rather then Enumeration<String>, need to convert
    final Enumeration<String> en = new Enumeration<String>() {
      public boolean hasMoreElements() {
        return tokenizer.hasMoreElements();
      }

      public String nextElement() {
        return (String)tokenizer.nextElement();
      }
    };
    return FilteringIterator.create(iterate(en), new Condition<String>() {
      public boolean value(String element) {
        element = element.trim();
        return element.length() != 0 && !myPathSet.contains(element);
      }
    });
  }

  private void addAllLast(Iterator<String> elememts, List<String> toArray) {
    while (elememts.hasNext()) {
      final String element = elememts.next();
      toArray.add(element);
      myPathSet.add(element);
    }
  }

  public String getPathsString() {
    final StringBuffer buffer = new StringBuffer();
    String separator = "";
    final List<String> classPath = getPathList();
    for (final String path : classPath) {
      buffer.append(separator);
      buffer.append(path);
      separator = File.pathSeparator;
    }
    return buffer.toString();
  }

  public List<String> getPathList() {
    final List<String> result = new ArrayList<String>();
    result.addAll(myPath);
    result.addAll(myPathTail);
    return result;
  }

  /**
   * @return {@link VirtualFile}s on local file system (returns jars as files).
   */
  public List<VirtualFile> getVirtualFiles() {
    return skipNulls(map(getPathList(), PATH_TO_LOCAL_VFILE));
  }

  /**
   * @return The same as {@link #getVirtualFiles()} but returns jars as {@link JarFileSystem} roots.
   */
  public List<VirtualFile> getRootDirs() {
    return skipNulls(map(getPathList(), PATH_TO_DIR));
  }

  public void addAll(List<String> allClasspath) {
    for (String path : allClasspath) {
      add(path);
    }
  }

  public void addAllFiles(File[] classpathList) {
    addAllFiles(Arrays.asList(classpathList));
  }
  
  public void addAllFiles(List<File> classpathList) {
    for (File file : classpathList) {
      add(file);
    }
  }

  public void add(File file) {
    add(PathUtil.getCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
  }

  public void addVirtualFiles(List<VirtualFile> files) {
    for (final VirtualFile file : files) {
      add(file);
    }
  }
}
