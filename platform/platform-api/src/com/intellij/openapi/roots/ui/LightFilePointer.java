/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.roots.ui;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

public class LightFilePointer implements VirtualFilePointer {
  private final String myUrl;
  private VirtualFile myFile;

  public LightFilePointer(@NotNull String url) {
    myUrl = url;
  }

  public LightFilePointer(@NotNull VirtualFile file) {
    myUrl = file.getUrl();
    myFile = file;
  }

  @Override
  @Nullable
  public VirtualFile getFile() {
    refreshFile();
    return myFile;
  }

  @Override
  @NotNull
  public String getUrl() {
    return myUrl;
  }

  @Override
  @NotNull
  public String getFileName() {
    if (myFile != null) {
      return myFile.getName();
    }
    int index = myUrl.lastIndexOf('/');
    return index >= 0 ? myUrl.substring(index + 1) : myUrl;
  }

  @Override
  @NotNull
  public String getPresentableUrl() {
    VirtualFile file = getFile();
    if (file != null) return file.getPresentableUrl();
    return toPresentableUrl(myUrl);
  }

  public static String toPresentableUrl(String url) {
    String path = VirtualFileManager.extractPath(url);
    path = StringUtil.trimEnd(path, JarFileSystem.JAR_SEPARATOR);
    return path.replace('/', File.separatorChar);
  }

  @Override
  public boolean isValid() {
    return getFile() != null;
  }

  private void refreshFile() {
    if (myFile != null && myFile.isValid()) return;
    VirtualFile virtualFile = VirtualFileManager.getInstance().findFileByUrl(myUrl);
    myFile = virtualFile != null && virtualFile.isValid() ? virtualFile : null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof LightFilePointer)) return false;

    return myUrl.equals(((LightFilePointer)o).myUrl);

  }

  @Override
  public int hashCode() {
    return myUrl.hashCode();
  }
}
