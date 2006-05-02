/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.*;
import com.intellij.util.containers.HashSet;
import org.jetbrains.annotations.NonNls;

import java.io.File;
import java.util.*;

public class PathsList  {
  private final List<String>  myPath = new ArrayList<String>();
  private final List<String> myPathTail = new ArrayList<String>();
  private final Set<String> myPathSet = new HashSet<String>();

  private static final EmptyIterator<String> ADD_NOTHING = new EmptyIterator<String>();

  private static final Convertor<String, VirtualFile> PATH_TO_LOCAL_VFILE = new Convertor<String, VirtualFile>() {
    public VirtualFile convert(String path) {
      return LocalFileSystem.getInstance().findFileByPath(path.replace(File.separatorChar, '/'));
    }
  };

  private static final Convertor<VirtualFile, String> LOCAL_PATH =
    new Convertor<VirtualFile, String>() {
      public String convert(VirtualFile file) {
        return PathUtil.getLocalPath(file);
      }
    };

  private static final Convertor<String,VirtualFile> PATH_TO_DIR =
    new Convertor<String, VirtualFile>() {
      public VirtualFile convert(String s) {
        final FileType fileType = FileTypeManager.getInstance().getFileTypeByFileName(s);
        if (StdFileTypes.ARCHIVE.equals(fileType)) {
          return JarFileSystem.getInstance().findFileByPath(s + JarFileSystem.JAR_SEPARATOR);
        }
        return PATH_TO_LOCAL_VFILE.convert(s);
      }
    };

  public void add(@NonNls String path) {
    addAllLast(chooseFirstTimeItems(path), myPath);
  }

  public void add(VirtualFile file) {
    add(LOCAL_PATH.convert(file));
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
      return ADD_NOTHING;
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
    return FilteringIterator.create(ContainerUtil.iterate(en), new Condition<String>() {
      public boolean value(String element) {
        element = element.trim();
        if (element.length() == 0) {
          return false;
        }
        return !myPathSet.contains(element);
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
    for (Iterator iterator = classPath.iterator(); iterator.hasNext();) {
      String path = (String) iterator.next();
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
    return CollectUtil.SKIP_NULLS.toList(getPathList().iterator(), PATH_TO_LOCAL_VFILE);
  }

  /**
   * @return The same as {@link #getVirtualFiles()} but returns jars as {@link JarFileSystem} roots.
   */
  public List<VirtualFile> getRootDirs() {
    return CollectUtil.SKIP_NULLS.toList(getPathList().iterator(), PATH_TO_DIR);
  }

  public void addAll(List<String> allClasspath) {
    for (Iterator<String> iterator = allClasspath.iterator(); iterator.hasNext();) {
      String path = iterator.next();
      add(path);
    }
  }

  public void addAllFiles(List<File> classpathList) {
    for (Iterator<File> iterator = classpathList.iterator(); iterator.hasNext();) {
      File file = iterator.next();
      add(PathUtil.getCanonicalPath(file.getAbsolutePath()).replace('/', File.separatorChar));
    }
  }

}
