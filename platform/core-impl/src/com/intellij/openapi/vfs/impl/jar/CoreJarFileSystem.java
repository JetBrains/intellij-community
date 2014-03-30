/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vfs.impl.jar;

import com.intellij.openapi.vfs.DeprecatedVirtualFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author yole
 */
public class CoreJarFileSystem extends DeprecatedVirtualFileSystem {
  private final Map<String, CoreJarHandler> myHandlers = new HashMap<String, CoreJarHandler>();

  @NotNull
  @Override
  public String getProtocol() {
    return StandardFileSystems.JAR_PROTOCOL;
  }

  @Override
  public VirtualFile findFileByPath(@NotNull @NonNls String path) {
    int separatorIndex = path.indexOf("!/");
    if (separatorIndex < 0) {
      throw new IllegalArgumentException("path in JarFileSystem must contain a separator");
    }
    String localPath = path.substring(0, separatorIndex);
    String pathInJar = path.substring(separatorIndex+2);
    CoreJarHandler handler = getHandler(localPath);
    return handler.findFileByPath(pathInJar);
  }

  @NotNull
  private CoreJarHandler getHandler(String localPath) {
    CoreJarHandler handler = myHandlers.get(localPath);
    if (handler == null) {
      handler = new CoreJarHandler(this, localPath);
      myHandlers.put(localPath, handler);
    }
    return handler;
  }

  @Override
  public void refresh(boolean asynchronous) {
  }

  @Override
  public VirtualFile refreshAndFindFileByPath(@NotNull String path) {
    return findFileByPath(path);
  }

  @Override
  protected void deleteFile(Object requestor, @NotNull VirtualFile vFile) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @Override
  protected void moveFile(Object requestor, @NotNull VirtualFile vFile, @NotNull VirtualFile newParent) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @Override
  protected void renameFile(Object requestor, @NotNull VirtualFile vFile, @NotNull String newName) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @Override
  protected VirtualFile createChildFile(Object requestor, @NotNull VirtualFile vDir, @NotNull String fileName) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @NotNull
  @Override
  protected VirtualFile createChildDirectory(Object requestor, @NotNull VirtualFile vDir, @NotNull String dirName) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }

  @Override
  protected VirtualFile copyFile(Object requestor,
                                 @NotNull VirtualFile virtualFile,
                                 @NotNull VirtualFile newParent,
                                 @NotNull String copyName) throws IOException {
    throw new UnsupportedOperationException("JarFileSystem is read-only");
  }
}
