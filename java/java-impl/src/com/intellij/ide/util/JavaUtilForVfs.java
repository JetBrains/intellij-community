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
package com.intellij.ide.util;

import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;


public class JavaUtilForVfs {
  private JavaUtilForVfs() {}

  /**
   * Scan directory and detect java source roots within it. The source root is detected as the following:
   * <ol>
   * <li>It contains at least one Java file.</li>
   * <li>Java file is located in the subfolder that matches package statement in the file.</li>
   * </ol>
   * @param dir a directory to scan
   * @return a list of found source roots within directory. If no source roots are found, a empty list is returned.
   */
  public static List<VirtualFile> suggestRoots(VirtualFile dir) {
    ArrayList<VirtualFile> foundDirectories = new ArrayList<VirtualFile>();
    try{
      suggestRootsImpl(dir, foundDirectories);
    }
    catch(PathFoundException ignore){
    }
    return foundDirectories;
  }

  private static class PathFoundException extends Exception {
    public VirtualFile myDirectory;

    public PathFoundException(VirtualFile directory) {
      myDirectory = directory;
    }
  }

  private static void suggestRootsImpl(VirtualFile dir, ArrayList<? super VirtualFile> foundDirectories) throws PathFoundException {
    if (!dir.isDirectory()) {
      return;
    }
    FileTypeManager typeManager = FileTypeManager.getInstance();
    final String dirName = dir.getName();
    if (typeManager.isFileIgnored(dirName) || StringUtil.startsWithIgnoreCase(dirName, "testdata")) {
      return;
    }
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      if (progressIndicator.isCanceled()) {
        return;
      }
      progressIndicator.setText2(dir.getPath());
    }

    VirtualFile[] list = dir.getChildren();
    if (list == null || list.length == 0) {
      return;
    }

    for (VirtualFile child : list) {
      if (!child.isDirectory()) {
        FileType type = typeManager.getFileTypeByFileName(child.getName());
        if (StdFileTypes.JAVA == type) {
          if (progressIndicator != null && progressIndicator.isCanceled()) {
            return;
          }

          VirtualFile root = suggestRootForJavaFile(child);
          if (root != null) {
            foundDirectories.add(root);
            throw new PathFoundException(root);
          }
          else {
            return;
          }
        }
      }
    }

    for (VirtualFile child : list) {
      if (child.isDirectory()) {
        try {
          suggestRootsImpl(child, foundDirectories);
        }
        catch (PathFoundException found) {
          if (!found.myDirectory.equals(child)) {
            throw found;
          }
        }
      }
    }
  }

  @Nullable
  private static VirtualFile suggestRootForJavaFile(VirtualFile javaFile) {
    if (javaFile.isDirectory()) return null;

    CharSequence chars = LoadTextUtil.loadText(javaFile);

    String packageName = JavaUtil.getPackageStatement(chars);
    if (packageName != null){
      VirtualFile root = javaFile.getParent();
      int index = packageName.length();
      while(index > 0){
        int index1 = packageName.lastIndexOf('.', index - 1);
        String token = packageName.substring(index1 + 1, index);
        String dirName = root.getName();
        final boolean equalsToToken = SystemInfo.isFileSystemCaseSensitive ? dirName.equals(token) : dirName.equalsIgnoreCase(token);
        if (!equalsToToken) {
          return null;
        }
        root = root.getParent();
        if (root == null){
          return null;
        }
        index = index1;
      }
      return root;
    }

    return null;
  }
}
