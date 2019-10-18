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
package com.intellij.openapi.vfs.impl;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileSystem;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LightFilePointer implements VirtualFilePointer {
  
  @NotNull
  private final String myUrl;
  @Nullable
  private volatile VirtualFile myFile;
  private volatile boolean myRefreshed;

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
    VirtualFile file = myFile;
    if (file != null) {
      return file.getName();
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

  @NotNull
  private static String toPresentableUrl(@NotNull String url) {
    String path = VirtualFileManager.extractPath(url);
    String protocol = VirtualFileManager.extractProtocol(url);
    VirtualFileSystem fileSystem = VirtualFileManager.getInstance().getFileSystem(protocol);
    return ObjectUtils.notNull(fileSystem, StandardFileSystems.local()).extractPresentableUrl(path);
  }

  @Override
  public boolean isValid() {
    return getFile() != null;
  }

  private void refreshFile() {
    VirtualFile file = myFile;
    if (file != null && file.isValid()) return;
    VirtualFileManager vfManager = VirtualFileManager.getInstance();
    VirtualFile virtualFile = vfManager.findFileByUrl(myUrl);
    if (virtualFile == null && !myRefreshed) {
      myRefreshed = true;
      Application application = ApplicationManager.getApplication();
      if (application.isDispatchThread() || !application.isReadAccessAllowed()) {
        virtualFile = vfManager.refreshAndFindFileByUrl(myUrl);
      }
      else {
        application.executeOnPooledThread(() -> vfManager.refreshAndFindFileByUrl(myUrl));
      }
    }
    
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
