/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.vfs;

import com.intellij.openapi.util.io.BufferExposingByteArrayInputStream;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.HashSet;
import java.util.Set;

public class VfsUtilCore {
  /**
   * Checks whether the <code>ancestor {@link com.intellij.openapi.vfs.VirtualFile}</code> is parent of <code>file
   * {@link com.intellij.openapi.vfs.VirtualFile}</code>.
   *
   * @param ancestor the file
   * @param file     the file
   * @param strict   if <code>false</code> then this method returns <code>true</code> if <code>ancestor</code>
   *                 and <code>file</code> are equal
   * @return <code>true</code> if <code>ancestor</code> is parent of <code>file</code>; <code>false</code> otherwise
   */
  public static boolean isAncestor(@NotNull VirtualFile ancestor, @NotNull VirtualFile file, boolean strict) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return false;
    VirtualFile parent = strict ? file.getParent() : file;
    while (true) {
      if (parent == null) return false;
      if (parent.equals(ancestor)) return true;
      parent = parent.getParent();
    }
  }

  /**
   * Gets the relative path of <code>file</code> to its <code>ancestor</code>. Uses <code>separator</code> for
   * separating files.
   *
   * @param file      the file
   * @param ancestor  parent file
   * @param separator character to use as files separator
   * @return the relative path or {@code null} if {@code ancestor} is not ancestor for {@code file}
   */
  @Nullable
  public static String getRelativePath(@NotNull VirtualFile file, @NotNull VirtualFile ancestor, char separator) {
    if (!file.getFileSystem().equals(ancestor.getFileSystem())) return null;

    int length = 0;
    VirtualFile parent = file;
    while (true) {
      if (parent == null) return null;
      if (parent.equals(ancestor)) break;
      if (length > 0) {
        length++;
      }
      length += parent.getName().length();
      parent = parent.getParent();
    }

    char[] chars = new char[length];
    int index = chars.length;
    parent = file;
    while (true) {
      if (parent.equals(ancestor)) break;
      if (index < length) {
        chars[--index] = separator;
      }
      String name = parent.getName();
      for (int i = name.length() - 1; i >= 0; i--) {
        chars[--index] = name.charAt(i);
      }
      parent = parent.getParent();
    }
    return new String(chars);
  }

  @Nullable
  public static VirtualFile getVirtualFileForJar(@Nullable VirtualFile entryVFile) {
    if (entryVFile == null) return null;
    final String path = entryVFile.getPath();
    final int separatorIndex = path.indexOf("!/");
    if (separatorIndex < 0) return null;

    String localPath = path.substring(0, separatorIndex);
    return VirtualFileManager.getInstance().findFileByUrl("file://" + localPath);
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @return a copy of the file
   * @throws java.io.IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir) throws IOException {
    return copyFile(requestor, file, toDir, file.getName());
  }

  /**
   * Makes a copy of the <code>file</code> in the <code>toDir</code> folder with the <code>newName</code> and returns it.
   *
   * @param requestor any object to control who called this method. Note that
   *                  it is considered to be an external change if <code>requestor</code> is <code>null</code>.
   *                  See {@link com.intellij.openapi.vfs.VirtualFileEvent#getRequestor}
   * @param file      file to make a copy of
   * @param toDir     directory to make a copy in
   * @param newName   new name of the file
   * @return a copy of the file
   * @throws java.io.IOException if file failed to be copied
   */
  public static VirtualFile copyFile(Object requestor, @NotNull VirtualFile file, @NotNull VirtualFile toDir, @NotNull @NonNls String newName)
    throws IOException {
    final VirtualFile newChild = toDir.createChildData(requestor, newName);
    // [jeka] TODO: to be discussed if the copy should have the same timestamp as the original
    //OutputStream out = newChild.getOutputStream(requestor, -1, file.getActualTimeStamp());
    newChild.setBinaryContent(file.contentsToByteArray());
    return newChild;
  }

  @NotNull
  public static InputStream byteStreamSkippingBOM(@NotNull byte[] buf, @NotNull VirtualFile file) throws IOException {
    BufferExposingByteArrayInputStream stream = new BufferExposingByteArrayInputStream(buf);
    return inputStreamSkippingBOM(stream, file);
  }

  @NotNull
  public static InputStream inputStreamSkippingBOM(@NotNull InputStream stream, @NotNull VirtualFile file) throws IOException {
    return CharsetToolkit.inputStreamSkippingBOM(stream);
  }

  @NotNull
  public static OutputStream outputStreamAddingBOM(@NotNull OutputStream stream, @NotNull VirtualFile file) throws IOException {
    byte[] bom = file.getBOM();
    if (bom != null) {
      stream.write(bom);
    }
    return stream;
  }

  public static void visitChildrenRecursively(@NotNull VirtualFile file, VirtualFileVisitor visitor) {
    visitChildrenRecursively(file, visitor, null);
  }

  private static void visitChildrenRecursively(@NotNull VirtualFile file,
                                               VirtualFileVisitor visitor,
                                               @Nullable Set<VirtualFile> visitedSymlinks) {

    if (!visitor.visitFile(file)) return;
    if (file.isSymLink()) {
      if (visitedSymlinks == null) {
        visitedSymlinks = new HashSet<VirtualFile>();
      }
      if (!visitedSymlinks.add(file)) {
        visitor.afterChildrenVisited(file);
        return;
      }
    }
    VirtualFile[] children = file.getChildren();
    for (VirtualFile child : children) {
      visitChildrenRecursively(child, visitor, visitedSymlinks);
    }
    visitor.afterChildrenVisited(file);
  }

  public static String loadText(@NotNull VirtualFile file) throws IOException{
    InputStreamReader reader = new InputStreamReader(file.getInputStream(), file.getCharset());
    try {
      return new String(FileUtil.loadText(reader, (int)file.getLength()));
    }
    finally {
      reader.close();
    }
  }

  public static boolean isAncestor(@NotNull File ancestor, @NotNull File file, boolean strict) {
    File parent = strict ? file.getParentFile() : file;
    while (parent != null) {
      if (parent.equals(ancestor)) return true;
      parent = parent.getParentFile();
    }

    return false;
  }
}
